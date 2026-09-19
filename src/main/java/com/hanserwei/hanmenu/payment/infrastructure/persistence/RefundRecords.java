package com.hanserwei.hanmenu.payment.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** Refund 的有界派生查询和事务行锁. */
interface RefundRecords extends JpaRepository<RefundEntity, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<RefundEntity> findLockedById(UUID id);

  Optional<RefundEntity> findByPaymentId(UUID paymentId);

  List<RefundEntity> findByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(
      Instant now, Pageable pageable);
}
