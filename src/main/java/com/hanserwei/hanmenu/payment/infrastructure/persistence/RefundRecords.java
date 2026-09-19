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
interface RefundRecords
    extends JpaRepository<RefundEntity, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<RefundEntity> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<RefundEntity> findLockedById(UUID id);

  Optional<RefundEntity> findByPaymentId(UUID paymentId);

  List<RefundEntity> findByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(
      Instant now, Pageable pageable);

  List<RefundEntity> findByStatusOrderByIdAsc(
      com.hanserwei.hanmenu.payment.domain.Refund.Status status, Pageable page);

  List<RefundEntity> findByStatusAndIdGreaterThanOrderByIdAsc(
      com.hanserwei.hanmenu.payment.domain.Refund.Status status, UUID cursor, Pageable page);
}
