package com.hanserwei.hanmenu.payment.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** Payment 的有界派生查询和事务行锁. */
interface PaymentRecords extends JpaRepository<PaymentEntity, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<PaymentEntity> findLockedById(UUID id);

  Optional<PaymentEntity> findByCustomerIdAndIdempotencyKey(UUID customerId, String key);

  Optional<PaymentEntity> findByBusinessRef(UUID businessRef);

  List<PaymentEntity> findByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(
      Instant now, Pageable pageable);

  List<PaymentEntity> findByStatusOrderByIdAsc(
      com.hanserwei.hanmenu.payment.domain.Payment.Status status, Pageable page);

  List<PaymentEntity> findByStatusAndIdGreaterThanOrderByIdAsc(
      com.hanserwei.hanmenu.payment.domain.Payment.Status status, UUID cursor, Pageable page);
}
