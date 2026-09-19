package com.hanserwei.hanmenu.ordering.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 订单聚合维护付款、取消与履约状态，金额和收货快照终身不变. */
public final class Order {
  private final UUID id;
  private final UUID customerId;
  private final String idempotencyKey;
  private final String requestFingerprint;
  private final AddressSnapshot address;
  private final List<OrderLine> lines;
  private final BigDecimal total;
  private final long version;
  private final Instant createdAt;
  private Status status;
  private Instant cancelledAt;
  private UUID paymentId;
  private Instant paidAt;
  private Instant acceptedAt;
  private Instant deliveredAt;
  private Instant completedAt;
  private CancelReason cancelReason;
  private RefundStatus refundStatus;
  private UUID refundId;

  /** 重建持久化快照并核对状态、金额及时间的一致性. */
  public Order(
      UUID id,
      UUID customerId,
      String idempotencyKey,
      String requestFingerprint,
      AddressSnapshot address,
      List<OrderLine> lines,
      Status status,
      long version,
      Instant createdAt,
      Instant cancelledAt,
      Lifecycle lifecycle) {
    this.id = Objects.requireNonNull(id);
    this.customerId = Objects.requireNonNull(customerId);
    this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
    this.requestFingerprint = Objects.requireNonNull(requestFingerprint);
    this.address = Objects.requireNonNull(address);
    this.lines = List.copyOf(lines);
    this.status = Objects.requireNonNull(status);
    this.version = version;
    this.createdAt = Objects.requireNonNull(createdAt);
    this.cancelledAt = cancelledAt;
    this.paymentId = lifecycle.paymentId();
    this.paidAt = lifecycle.paidAt();
    this.acceptedAt = lifecycle.acceptedAt();
    this.deliveredAt = lifecycle.deliveredAt();
    this.completedAt = lifecycle.completedAt();
    this.cancelReason = lifecycle.cancelReason();
    this.refundStatus = Objects.requireNonNull(lifecycle.refundStatus());
    this.refundId = lifecycle.refundId();
    if (lines.isEmpty()
        || lines.size() > 50
        || version < 0
        || lines.stream().map(OrderLine::id).distinct().count() != lines.size()
        || (status == Status.CANCELLED) != (cancelledAt != null)
        || (cancelledAt != null && cancelledAt.isBefore(createdAt))) {
      throw new OrderException(OrderException.Reason.INVALID_INPUT, "订单快照不合法");
    }
    this.total =
        lines.stream().map(OrderLine::subtotal).reduce(new BigDecimal("0.00"), BigDecimal::add);
  }

  /** 新建待付款订单，服务端只接受已重新校验的快照. */
  public static Order submit(
      UUID customerId,
      String key,
      String fingerprint,
      AddressSnapshot address,
      List<OrderLine> lines,
      Instant now) {
    return new Order(
        UUID.randomUUID(),
        customerId,
        key,
        fingerprint,
        address,
        lines,
        Status.UNPAID,
        0,
        now,
        null,
        new Lifecycle(null, null, null, null, null, null, RefundStatus.NONE, null));
  }

  /** 校验客户端版本，拒绝陈旧修改. */
  public void requireVersion(long expected) {
    if (expected != version) {
      throw new OrderException(OrderException.Reason.VERSION_CONFLICT, "订单已被修改");
    }
  }

  /** 同键必须对应相同规范化请求，已取消订单也不能复用该键重新建单. */
  public void requireSameRequest(String fingerprint) {
    if (!requestFingerprint.equals(fingerprint)) {
      throw new OrderException(OrderException.Reason.IDEMPOTENCY_CONFLICT, "幂等键已用于其他请求");
    }
  }

  /** 顾客在接单前申请取消；存在支付意图时先等待渠道关单或退款确认. */
  public void cancel(long expectedVersion, Instant now) {
    requestCancellation(expectedVersion, CancelReason.CUSTOMER, now);
  }

  /** 固定十五分钟支付窗口，后续支付创建或重试不能延长. */
  public Instant expiresAt() {
    return createdAt.plusSeconds(900);
  }

  /** 绑定唯一支付意图，过期、非待付款或已有意图的订单不能重新发起付款. */
  public void attachPayment(UUID id, long expectedVersion, Instant now) {
    requireVersion(expectedVersion);
    if (status != Status.UNPAID || paymentId != null || !now.isBefore(expiresAt())) {
      conflict("订单不能创建新的支付单");
    }
    paymentId = Objects.requireNonNull(id);
  }

  /** 保存取消意图，不把网络调用受理或结果未知当作取消完成. */
  public void requestCancellation(long expectedVersion, CancelReason reason, Instant now) {
    requireVersion(expectedVersion);
    boolean allowed =
        switch (reason) {
          case CUSTOMER -> status == Status.UNPAID || status == Status.PAID;
          case TIMEOUT -> status == Status.UNPAID && !now.isBefore(expiresAt());
          case MERCHANT_REJECTED -> status == Status.PAID;
          case MERCHANT_CANCELLED -> status == Status.PAID || status == Status.ACCEPTED;
          case PAYMENT_CLOSED -> false;
        };
    if (!allowed) {
      conflict("当前订单状态不能执行该取消操作");
    }
    cancelReason = reason;
    if (status == Status.UNPAID) {
      if (paymentId == null) {
        finishCancellation(now);
      } else {
        status = Status.CANCELLING;
      }
    } else {
      status = Status.REFUNDING;
      refundStatus = RefundStatus.PENDING;
    }
  }

  /** 消费真实付款事实；迟到成功触发退款，已取消订单不能恢复履约. */
  public boolean paymentSucceeded(
      UUID payment, BigDecimal amount, Instant paymentTime, Instant now) {
    requirePayment(payment, amount);
    if (paidAt != null) {
      return refundStatus == RefundStatus.PENDING;
    }
    paidAt = Objects.requireNonNull(paymentTime);
    if (refundStatus == RefundStatus.SUCCEEDED) {
      return false;
    }
    if (status == Status.CANCELLED
        || status == Status.CANCELLING
        || !paymentTime.isBefore(expiresAt())) {
      if (cancelReason == null) {
        cancelReason = CancelReason.TIMEOUT;
      }
      refundStatus = RefundStatus.PENDING;
      if (status != Status.CANCELLED) {
        status = Status.REFUNDING;
      }
      return true;
    }
    if (status == Status.UNPAID) {
      status = Status.PAID;
    }
    return false;
  }

  /** 支付已成功时忽略乱序关闭；只有未付款取消流程可以由关单结果终结. */
  public void paymentClosed(UUID payment, BigDecimal amount, Instant now) {
    requirePayment(payment, amount);
    if (paidAt == null && (status == Status.UNPAID || status == Status.CANCELLING)) {
      if (cancelReason == null) {
        cancelReason = CancelReason.PAYMENT_CLOSED;
      }
      finishCancellation(now);
    }
  }

  /** 退款确认事件幂等完成取消，不允许将已履约订单随意改为退款成功. */
  public void refundSucceeded(UUID payment, UUID refund, BigDecimal amount, Instant now) {
    requirePayment(payment, amount);
    if (refundStatus == RefundStatus.SUCCEEDED) {
      if (!Objects.equals(refundId, refund)) {
        conflict("退款标识不一致");
      }
      return;
    }
    if (status != Status.REFUNDING && status != Status.CANCELLING && status != Status.CANCELLED) {
      conflict("订单没有退款意图");
    }
    refundId = Objects.requireNonNull(refund);
    refundStatus = RefundStatus.SUCCEEDED;
    if (cancelReason == null) {
      cancelReason = CancelReason.PAYMENT_CLOSED;
    }
    if (status != Status.CANCELLED) {
      finishCancellation(now);
    }
  }

  /** 商家只能接收已由渠道确认付款的订单. */
  public void accept(long expectedVersion, Instant now) {
    requireVersion(expectedVersion);
    requireStatus(Status.PAID);
    status = Status.ACCEPTED;
    acceptedAt = now;
  }

  /** 接单后开始配送，不能跳过付款和接单. */
  public void deliver(long expectedVersion, Instant now) {
    requireVersion(expectedVersion);
    requireStatus(Status.ACCEPTED);
    status = Status.DELIVERING;
    deliveredAt = now;
  }

  /** 配送结束后完成订单，终态不再允许取消或重复完成. */
  public void complete(long expectedVersion, Instant now) {
    requireVersion(expectedVersion);
    requireStatus(Status.DELIVERING);
    status = Status.COMPLETED;
    completedAt = now;
  }

  private void requireStatus(Status expected) {
    if (status != expected) {
      conflict("订单状态不允许该履约操作");
    }
  }

  private void requirePayment(UUID payment, BigDecimal amount) {
    if (!Objects.equals(paymentId, payment) || amount == null || total.compareTo(amount) != 0) {
      conflict("支付引用或金额不匹配");
    }
  }

  private void finishCancellation(Instant now) {
    if (now.isBefore(createdAt)) {
      throw new IllegalArgumentException("取消时间早于创建时间");
    }
    status = Status.CANCELLED;
    cancelledAt = now;
  }

  private void conflict(String message) {
    throw new OrderException(OrderException.Reason.STATE_CONFLICT, message);
  }

  /** 返回不可变生命周期快照，供持久化和响应集中映射. */
  public Lifecycle lifecycle() {
    return new Lifecycle(
        paymentId,
        paidAt,
        acceptedAt,
        deliveredAt,
        completedAt,
        cancelReason,
        refundStatus,
        refundId);
  }

  /** 状态迁移只能由聚合行为完成，不提供任意状态 setter. */
  public enum Status {
    UNPAID,
    PAID,
    ACCEPTED,
    DELIVERING,
    COMPLETED,
    CANCELLING,
    REFUNDING,
    CANCELLED
  }

  /** 取消原因采用固定业务枚举，不保存敏感自由文本. */
  public enum CancelReason {
    CUSTOMER,
    TIMEOUT,
    MERCHANT_REJECTED,
    MERCHANT_CANCELLED,
    PAYMENT_CLOSED
  }

  /** 已取消订单的迟到付款可独立显示退款处理中而不恢复订单状态. */
  public enum RefundStatus {
    NONE,
    PENDING,
    SUCCEEDED
  }

  /** 不可变的生命周期持久化快照，行为仍由 Order 聚合维护. */
  public record Lifecycle(
      UUID paymentId,
      Instant paidAt,
      Instant acceptedAt,
      Instant deliveredAt,
      Instant completedAt,
      CancelReason cancelReason,
      RefundStatus refundStatus,
      UUID refundId) {}

  /** 返回订单标识. */
  public UUID id() {
    return id;
  }

  /** 返回顾客标识. */
  public UUID customerId() {
    return customerId;
  }

  /** 返回提交幂等键. */
  public String idempotencyKey() {
    return idempotencyKey;
  }

  /** 返回规范化请求摘要. */
  public String requestFingerprint() {
    return requestFingerprint;
  }

  /** 返回不可变地址快照. */
  public AddressSnapshot address() {
    return address;
  }

  /** 返回不可变条目快照. */
  public List<OrderLine> lines() {
    return lines;
  }

  /** 返回人民币总金额. */
  public BigDecimal total() {
    return total;
  }

  /** 返回持久化版本. */
  public long version() {
    return version;
  }

  /** 返回创建时刻. */
  public Instant createdAt() {
    return createdAt;
  }

  /** 返回订单状态. */
  public Status status() {
    return status;
  }

  /** 返回取消时刻. */
  public Instant cancelledAt() {
    return cancelledAt;
  }
}
