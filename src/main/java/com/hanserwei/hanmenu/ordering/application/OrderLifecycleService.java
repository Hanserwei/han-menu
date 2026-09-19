package com.hanserwei.hanmenu.ordering.application;

import com.hanserwei.hanmenu.customer.api.CustomerCheckout;
import com.hanserwei.hanmenu.customer.api.CustomerIdentity;
import com.hanserwei.hanmenu.identity.api.StaffAuthorization;
import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import com.hanserwei.hanmenu.ordering.domain.Order;
import com.hanserwei.hanmenu.ordering.domain.OrderException;
import com.hanserwei.hanmenu.ordering.domain.OrderRepository;
import com.hanserwei.hanmenu.ordering.domain.OrderSearch;
import com.hanserwei.hanmenu.payment.api.PaymentOperations;
import com.hanserwei.hanmenu.payment.events.PaymentResult;
import com.hanserwei.hanmenu.payment.events.RefundResult;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 订单支付与履约短事务，和支付意图协作但不执行渠道网络请求. */
@Service
@Transactional
public class OrderLifecycleService {
  private final OrderRepository orders;
  private final CustomerCheckout customers;
  private final StaffAuthorization staff;
  private final PaymentOperations payments;
  private final Clock clock;
  private final OrderEvents events;

  /** 只依赖公开模块契约和本模块仓储. */
  public OrderLifecycleService(
      OrderRepository orders,
      CustomerCheckout customers,
      StaffAuthorization staff,
      PaymentOperations payments,
      Clock clock,
      OrderEvents events) {
    this.orders = orders;
    this.customers = customers;
    this.staff = staff;
    this.payments = payments;
    this.clock = clock;
    this.events = events;
  }

  /** 先锁顾客再锁订单；同键重试验证原意图而非拒绝已经变化的订单版本. */
  public PaymentOperations.Intent reserve(
      CustomerIdentity identity, UUID id, String key, long version) {
    customers.lockActive(identity);
    var order = ownLocked(identity, id);
    if (!payments.submitted(identity.customerId(), key)) {
      order.requireVersion(version);
    }
    var intent =
        payments.reserve(
            order.id(), identity.customerId(), order.total(), order.expiresAt(), key, version);
    if (order.lifecycle().paymentId() == null) {
      order.attachPayment(intent.id(), version, clock.instant());
      save(order);
    } else if (!order.lifecycle().paymentId().equals(intent.id())) {
      throw new OrderException(OrderException.Reason.STATE_CONFLICT, "支付单与订单不匹配");
    }
    return intent;
  }

  /** 顾客接单前取消，真实关单或退款通过持久化意图继续处理. */
  public OrderViews.Detail cancel(CustomerIdentity identity, UUID id, long version) {
    customers.lockActive(identity);
    var order = ownLocked(identity, id);
    order.cancel(version, clock.instant());
    saveCancellation(order);
    return OrderViews.detail(orders.findOwned(identity.customerId(), id).orElseThrow());
  }

  /** 员工按当前账号权限执行明确履约动作，不能直接设置任意订单状态. */
  public OrderViews.Detail act(StaffIdentity actor, UUID id, long version, Action action) {
    staff.requireStaff(actor);
    var order = orders.lock(id);
    switch (action) {
      case ACCEPT -> order.accept(version, clock.instant());
      case DELIVER -> order.deliver(version, clock.instant());
      case COMPLETE -> order.complete(version, clock.instant());
      case REJECT ->
          order.requestCancellation(version, Order.CancelReason.MERCHANT_REJECTED, clock.instant());
      case CANCEL ->
          order.requestCancellation(
              version, Order.CancelReason.MERCHANT_CANCELLED, clock.instant());
      default -> throw new IllegalArgumentException("未知履约动作");
    }
    if (action == Action.REJECT || action == Action.CANCEL) {
      saveCancellation(order);
    } else {
      save(order);
    }
    return OrderViews.detail(orders.find(id).orElseThrow());
  }

  /** 后台提供授权后的组合检索，计数与分页均在数据库执行. */
  @Transactional(readOnly = true)
  public OrderViews.History history(StaffIdentity actor, OrderSearch search) {
    staff.requireStaff(actor);
    return OrderViews.history(orders.management(search), search.page(), search.size());
  }

  /** 员工读取履约所需收货快照. */
  @Transactional(readOnly = true)
  public OrderViews.Detail detail(StaffIdentity actor, UUID id) {
    staff.requireStaff(actor);
    return OrderViews.detail(
        orders
            .find(id)
            .orElseThrow(() -> new OrderException(OrderException.Reason.NOT_FOUND, "订单不存在")));
  }

  /** 锁内应用付款结果，重复或乱序事件不重复接单、不恢复取消订单. */
  public void paymentResult(PaymentResult result) {
    var order = orders.lock(result.businessRef());
    if (!order.customerId().equals(result.customerId())) {
      throw new OrderException(OrderException.Reason.STATE_CONFLICT, "支付归属不一致");
    }
    var previous = order.status();
    if (result.status().equals("SUCCEEDED")) {
      if (order.paymentSucceeded(
          result.paymentId(), result.amount(), result.paidAt(), clock.instant())) {
        payments.refund(result.paymentId());
      }
    } else if (result.status().equals("CLOSED")) {
      order.paymentClosed(result.paymentId(), result.amount(), clock.instant());
    }
    save(order);
    if (previous == Order.Status.UNPAID && order.status() == Order.Status.PAID) {
      events.ready(order.id(), order.lifecycle().paidAt());
    }
  }

  /** 退款确认后结束取消；终态和退款号使重复投递保持幂等. */
  public void refundResult(RefundResult result) {
    var order = orders.lock(result.businessRef());
    order.refundSucceeded(result.paymentId(), result.refundId(), result.amount(), clock.instant());
    save(order);
  }

  /** 一次只获取最多一百个超时订单，避免无界事务. */
  @Transactional(readOnly = true)
  public List<UUID> expired() {
    return orders.expired(clock.instant().minusSeconds(900), 100);
  }

  /** 超时和付款消费者使用同一行锁竞争，已经推进的订单不会被取消任务覆盖. */
  public void expire(UUID id) {
    var order = orders.lock(id);
    if (order.status() == Order.Status.UNPAID && !clock.instant().isBefore(order.expiresAt())) {
      order.requestCancellation(order.version(), Order.CancelReason.TIMEOUT, clock.instant());
      saveCancellation(order);
    }
  }

  /** 当前顾客在订单锁内催单，状态、版本与冷却窗口全部通过领域验证. */
  public OrderViews.Detail remind(CustomerIdentity identity, UUID id, long version) {
    customers.lockActive(identity);
    var order = ownLocked(identity, id);
    order.remind(version, clock.instant());
    save(order);
    events.reminder(order);
    return OrderViews.detail(orders.find(id).orElseThrow());
  }

  private void save(Order order) {
    orders.update(order);
    events.changed(order);
  }

  private void saveCancellation(Order order) {
    if (order.lifecycle().paymentId() != null) {
      payments.stop(order.lifecycle().paymentId());
    }
    save(order);
  }

  private Order ownLocked(CustomerIdentity identity, UUID id) {
    var order = orders.lock(id);
    if (!order.customerId().equals(identity.customerId())) {
      throw new OrderException(OrderException.Reason.NOT_FOUND, "订单不存在");
    }
    return order;
  }

  /** 每个后台资源动作对应单一聚合行为. */
  public enum Action {
    ACCEPT,
    REJECT,
    CANCEL,
    DELIVER,
    COMPLETE
  }
}
