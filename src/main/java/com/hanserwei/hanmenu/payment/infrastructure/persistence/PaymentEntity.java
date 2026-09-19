package com.hanserwei.hanmenu.payment.infrastructure.persistence;

import com.hanserwei.hanmenu.payment.domain.Payment;
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

/** Payment 的持久化快照，业务状态只能由领域聚合产生. */
@Entity(name = "PaymentRecord")
@Table(name = "payment_intent")
public class PaymentEntity {
  @Id UUID id;
  UUID businessRef;
  UUID customerId;

  @Column(precision = 16, scale = 2)
  BigDecimal amount;

  String idempotencyKey;
  String fingerprint;
  Instant createdAt;
  Instant expiresAt;

  @Enumerated(EnumType.STRING)
  Payment.Status status;

  String tradeNo;
  Instant paidAt;
  boolean closeRequested;
  Instant nextAttemptAt;
  String lastFailure;
  @Version Long version;

  /** ORM 重建入口，不开放业务 setter. */
  protected PaymentEntity() {}

  static PaymentEntity from(Payment value) {
    var entity = new PaymentEntity();
    entity.id = value.id();
    entity.businessRef = value.businessRef();
    entity.customerId = value.customerId();
    entity.amount = value.amount();
    entity.idempotencyKey = value.idempotencyKey();
    entity.fingerprint = value.fingerprint();
    entity.createdAt = value.createdAt();
    entity.expiresAt = value.expiresAt();
    entity.apply(value);
    return entity;
  }

  void apply(Payment value) {
    status = value.status();
    tradeNo = value.tradeNo();
    paidAt = value.paidAt();
    closeRequested = value.closeRequested();
    nextAttemptAt = value.nextAttemptAt();
    lastFailure = value.lastFailure();
  }

  Payment domain() {
    return new Payment(
        id,
        businessRef,
        customerId,
        amount,
        idempotencyKey,
        fingerprint,
        createdAt,
        expiresAt,
        status,
        tradeNo,
        paidAt,
        closeRequested,
        nextAttemptAt,
        lastFailure,
        version);
  }
}
