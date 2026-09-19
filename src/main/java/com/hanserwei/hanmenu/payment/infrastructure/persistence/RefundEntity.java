package com.hanserwei.hanmenu.payment.infrastructure.persistence;

import com.hanserwei.hanmenu.payment.domain.Refund;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Refund 的持久化快照，业务状态只能由领域聚合产生. */
@Entity(name = "RefundRecord")
@Table(name = "payment_refund")
public class RefundEntity {
  @Id UUID id;
  UUID paymentId;
  UUID businessRef;
  UUID customerId;
  String tradeNo;

  @Column(precision = 16, scale = 2)
  BigDecimal amount;

  Instant createdAt;

  @Enumerated(EnumType.STRING)
  Refund.Status status;

  Instant confirmedAt;
  Instant nextAttemptAt;
  String lastFailure;
  @Version Long version;

  /** ORM 重建入口，不开放业务 setter. */
  protected RefundEntity() {}

  static RefundEntity from(Refund value) {
    var entity = new RefundEntity();
    entity.id = value.id();
    entity.paymentId = value.paymentId();
    entity.businessRef = value.businessRef();
    entity.customerId = value.customerId();
    entity.tradeNo = value.tradeNo();
    entity.amount = value.amount();
    entity.createdAt = value.createdAt();
    entity.apply(value);
    return entity;
  }

  void apply(Refund value) {
    status = value.status();
    confirmedAt = value.confirmedAt();
    nextAttemptAt = value.nextAttemptAt();
    lastFailure = value.lastFailure();
  }

  Refund domain() {
    return new Refund(
        id,
        paymentId,
        businessRef,
        customerId,
        tradeNo,
        amount,
        createdAt,
        status,
        confirmedAt,
        nextAttemptAt,
        lastFailure,
        version);
  }
}
