package com.hanserwei.hanmenu.payment.application;

import com.google.common.hash.Hashing;
import com.hanserwei.hanmenu.customer.api.CustomerAuthorization;
import com.hanserwei.hanmenu.customer.api.CustomerIdentity;
import com.hanserwei.hanmenu.payment.api.PaymentOperations;
import com.hanserwei.hanmenu.payment.domain.Payment;
import com.hanserwei.hanmenu.payment.domain.PaymentException;
import com.hanserwei.hanmenu.payment.domain.PaymentGateway;
import com.hanserwei.hanmenu.payment.domain.PaymentRepository;
import com.hanserwei.hanmenu.payment.domain.Refund;
import com.hanserwei.hanmenu.payment.events.PaymentResult;
import com.hanserwei.hanmenu.payment.events.RefundResult;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 支付短事务边界，只登记意图、应用渠道事实和发布同事务事件，不进行网络调用. */
@Service
@Transactional
public class PaymentTransactions {
  private final PaymentRepository repository;
  private final CustomerAuthorization customers;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  /** 组合仓储、身份与事务事件发布器. */
  public PaymentTransactions(
      PaymentRepository repository,
      CustomerAuthorization customers,
      ApplicationEventPublisher events,
      Clock clock) {
    this.repository = repository;
    this.customers = customers;
    this.events = events;
    this.clock = clock;
  }

  /** 在已经锁定订单及顾客的事务中登记唯一支付意图. */
  @Transactional(propagation = Propagation.MANDATORY)
  public PaymentOperations.Intent reserve(
      UUID businessRef,
      UUID customerId,
      BigDecimal amount,
      Instant expiresAt,
      String key,
      long orderVersion) {
    if (key == null || !key.matches("[A-Za-z0-9._:-]{1,128}")) {
      throw new PaymentException(PaymentException.Reason.INVALID_INPUT, "支付幂等键格式不合法");
    }
    String fingerprint =
        Hashing.sha256()
            .hashString(
                businessRef + ":" + amount.toPlainString() + ":" + expiresAt + ":" + orderVersion,
                StandardCharsets.UTF_8)
            .toString();
    var existing = repository.submitted(customerId, key);
    if (existing.isPresent()) {
      var payment = existing.orElseThrow();
      payment.requireSameRequest(fingerprint);
      return intent(payment, true);
    }
    if (repository.forBusiness(businessRef).isPresent()) {
      throw new PaymentException(PaymentException.Reason.CONFLICT, "订单已存在支付单，请查询原支付单");
    }
    var payment =
        Payment.create(
            businessRef, customerId, amount, key, fingerprint, clock.instant(), expiresAt);
    repository.add(payment);
    return intent(payment, false);
  }

  /** 查询已提交键供订单决定是否先应用客户端版本检查. */
  @Transactional(readOnly = true)
  public boolean submitted(UUID customerId, String key) {
    return repository.submitted(customerId, key).isPresent();
  }

  /** 订单取消只写入关闭或退款意图，渠道操作由可恢复工作器执行. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void stop(UUID id) {
    var payment = repository.lockPayment(id);
    payment.requestClose(clock.instant());
    repository.update(payment);
    if (payment.status() == Payment.Status.SUCCEEDED) {
      requestRefund(payment);
    }
  }

  /** 在原支付行锁下确保每笔支付只登记一个全额退款. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void refund(UUID id) {
    requestRefund(repository.lockPayment(id));
  }

  private void requestRefund(Payment payment) {
    if (repository.refundForPayment(payment.id()).isEmpty()) {
      repository.addRefund(Refund.create(payment, clock.instant()));
    }
  }

  /** 查询顾客自己的支付快照，未知和他人资源统一返回不存在. */
  @Transactional(readOnly = true)
  public Payment own(UUID id, UUID customerId) {
    return repository
        .payment(id)
        .filter(value -> value.customerId().equals(customerId))
        .orElseThrow(() -> new PaymentException(PaymentException.Reason.NOT_FOUND, "支付单不存在"));
  }

  /** 返回当前顾客的支付状态及退款引用. */
  @Transactional(readOnly = true)
  public PaymentView view(CustomerIdentity identity, UUID id) {
    customers.requireActive(identity);
    var payment = own(id, identity.customerId());
    return new PaymentView(
        payment.id(),
        payment.businessRef(),
        payment.status().name(),
        payment.amount(),
        "CNY",
        payment.expiresAt(),
        payment.paidAt(),
        payment.closeRequested(),
        payment.version(),
        repository.refundForPayment(id).map(Refund::id).orElse(null),
        payment.lastFailure());
  }

  /** 返回本人退款结果，不将处理中视为成功. */
  @Transactional(readOnly = true)
  public RefundView refundView(CustomerIdentity identity, UUID id) {
    customers.requireActive(identity);
    var refund =
        repository
            .refund(id)
            .filter(value -> value.customerId().equals(identity.customerId()))
            .orElseThrow(() -> new PaymentException(PaymentException.Reason.NOT_FOUND, "退款单不存在"));
    return new RefundView(
        refund.id(),
        refund.paymentId(),
        refund.businessRef(),
        refund.amount(),
        "CNY",
        refund.status().name(),
        refund.createdAt(),
        refund.confirmedAt(),
        refund.version(),
        refund.lastFailure());
  }

  /** 手动刷新也要求当前支付版本和有效身份. */
  @Transactional(readOnly = true)
  public void authorizeRefresh(CustomerIdentity identity, UUID id, long version) {
    customers.requireActive(identity);
    own(id, identity.customerId()).requireVersion(version);
  }

  /** 批量读取待对账任务标识，单次最多一百项. */
  @Transactional(readOnly = true)
  public List<UUID> duePayments() {
    return repository.duePayments(clock.instant(), 100);
  }

  /** 批量读取待退款任务标识，单次最多一百项. */
  @Transactional(readOnly = true)
  public List<UUID> dueRefunds() {
    return repository.dueRefunds(clock.instant(), 100);
  }

  /** 短事务领取支付任务，返回独立领域快照后立即释放数据库锁. */
  public Optional<Payment> claimPayment(UUID id) {
    var payment = repository.lockPayment(id);
    if (!payment.claim(clock.instant())) {
      return Optional.empty();
    }
    repository.update(payment);
    return Optional.of(payment);
  }

  /** 短事务领取退款任务，网络重试复用同一个退款标识. */
  public Optional<Refund> claimRefund(UUID id) {
    var refund = repository.lockRefund(id);
    if (!refund.claim(clock.instant())) {
      return Optional.empty();
    }
    repository.updateRefund(refund);
    return Optional.of(refund);
  }

  /** 应用可信查询或验签通知，状态和 Modulith 事件登记一同提交或回滚. */
  public void observe(UUID id, PaymentGateway.TradeResult result) {
    var payment = repository.lockPayment(id);
    boolean changed = payment.observe(result, clock.instant());
    repository.update(payment);
    if (payment.status() == Payment.Status.SUCCEEDED && payment.closeRequested()) {
      requestRefund(payment);
    }
    if (changed) {
      events.publishEvent(
          new PaymentResult(
              payment.id(),
              payment.businessRef(),
              payment.customerId(),
              payment.amount(),
              payment.status().name(),
              payment.paidAt(),
              clock.instant()));
    }
  }

  /** 将未知结果保存为可重试状态，不吞掉任务或改变付款事实. */
  public void paymentFailed(UUID id) {
    var value = repository.lockPayment(id);
    value.retry(clock.instant());
    repository.update(value);
  }

  /** 将退款故障保留为待确认任务. */
  public void refundFailed(UUID id) {
    var value = repository.lockRefund(id);
    value.retry(clock.instant());
    repository.updateRefund(value);
  }

  /** 只有渠道退款查询确认后才发布完成事件，重复调用幂等. */
  public void refundConfirmed(UUID id) {
    var refund = repository.lockRefund(id);
    if (refund.confirm(clock.instant())) {
      repository.updateRefund(refund);
      events.publishEvent(
          new RefundResult(
              refund.id(),
              refund.paymentId(),
              refund.businessRef(),
              refund.amount(),
              refund.confirmedAt()));
    }
  }

  private PaymentOperations.Intent intent(Payment payment, boolean replayed) {
    return new PaymentOperations.Intent(
        payment.id(), payment.status().name(), payment.expiresAt(), replayed);
  }

  /** 顾客支付查询模型不含签名串或个人资料. */
  public record PaymentView(
      UUID id,
      UUID orderId,
      String status,
      BigDecimal amount,
      String currency,
      Instant expiresAt,
      Instant paidAt,
      boolean closeRequested,
      long version,
      UUID refundId,
      String lastFailure) {}

  /** 顾客退款查询模型保留明确的处理状态. */
  public record RefundView(
      UUID id,
      UUID paymentId,
      UUID orderId,
      BigDecimal amount,
      String currency,
      String status,
      Instant createdAt,
      Instant confirmedAt,
      long version,
      String lastFailure) {}
}
