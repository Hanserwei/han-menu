package com.hanserwei.hanmenu.notification.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Attempt 的派生查询与必要事务锁. */
interface AttemptRecords extends JpaRepository<AttemptEntity, UUID> {
  List<AttemptEntity> findByNoticeIdOrderByStartedAtDescIdDesc(UUID id, Pageable page);
}
