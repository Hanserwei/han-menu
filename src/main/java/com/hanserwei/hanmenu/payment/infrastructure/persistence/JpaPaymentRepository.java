package com.hanserwei.hanmenu.payment.infrastructure.persistence;

import com.hanserwei.hanmenu.payment.domain.Payment;
import com.hanserwei.hanmenu.payment.domain.PaymentException;
import com.hanserwei.hanmenu.payment.domain.PaymentRepository;
import com.hanserwei.hanmenu.payment.domain.Refund;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 支付 ORM 仓储不发送网络请求，交易引用不关联订单模块的表. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JpaPaymentRepository implements PaymentRepository {
  private final PaymentRecords payments;
  private final RefundRecords refunds;

  JpaPaymentRepository(PaymentRecords payments, RefundRecords refunds) {
    this.payments = payments;
    this.refunds = refunds;
  }

  @Override
  public Optional<Payment> submitted(UUID customerId, String key) {
    return payments.findByCustomerIdAndIdempotencyKey(customerId, key).map(PaymentEntity::domain);
  }

  @Override
  public Optional<Payment> forBusiness(UUID businessRef) {
    return payments.findByBusinessRef(businessRef).map(PaymentEntity::domain);
  }

  @Override
  public Optional<Payment> payment(UUID id) {
    return payments.findById(id).map(PaymentEntity::domain);
  }

  @Override
  public Payment lockPayment(UUID id) {
    return payments
        .findLockedById(id)
        .orElseThrow(() -> new PaymentException(PaymentException.Reason.NOT_FOUND, "支付单不存在"))
        .domain();
  }

  @Override
  public void add(Payment value) {
    payments.saveAndFlush(PaymentEntity.from(value));
  }

  @Override
  public void update(Payment value) {
    var entity = payments.findById(value.id()).orElseThrow();
    if (entity.version != value.version()) {
      throw new ObjectOptimisticLockingFailureException(PaymentEntity.class, value.id());
    }
    entity.apply(value);
    payments.flush();
  }

  @Override
  public Optional<Refund> refundForPayment(UUID id) {
    return refunds.findByPaymentId(id).map(RefundEntity::domain);
  }

  @Override
  public Optional<Refund> refund(UUID id) {
    return refunds.findById(id).map(RefundEntity::domain);
  }

  @Override
  public Refund lockRefund(UUID id) {
    return refunds
        .findLockedById(id)
        .orElseThrow(() -> new PaymentException(PaymentException.Reason.NOT_FOUND, "退款单不存在"))
        .domain();
  }

  @Override
  public void addRefund(Refund value) {
    refunds.saveAndFlush(RefundEntity.from(value));
  }

  @Override
  public void updateRefund(Refund value) {
    var entity = refunds.findById(value.id()).orElseThrow();
    if (entity.version != value.version()) {
      throw new ObjectOptimisticLockingFailureException(RefundEntity.class, value.id());
    }
    entity.apply(value);
    refunds.flush();
  }

  @Override
  public List<UUID> duePayments(Instant now, int limit) {
    return payments
        .findByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(now, PageRequest.of(0, limit))
        .stream()
        .map(value -> value.id)
        .toList();
  }

  @Override
  public List<UUID> dueRefunds(Instant now, int limit) {
    return refunds
        .findByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(now, PageRequest.of(0, limit))
        .stream()
        .map(value -> value.id)
        .toList();
  }
}
