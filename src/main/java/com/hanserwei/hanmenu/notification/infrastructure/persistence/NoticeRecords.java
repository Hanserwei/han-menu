package com.hanserwei.hanmenu.notification.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** Notice 的派生查询与必要事务锁. */
interface NoticeRecords extends JpaRepository<NoticeEntity, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<NoticeEntity> findLockedById(UUID id);

  List<NoticeEntity> findBySequenceGreaterThanOrderBySequenceAsc(long after, Pageable page);

  List<NoticeEntity> findByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscSequenceAsc(
      Instant now, Pageable page);
}
