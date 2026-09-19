package com.hanserwei.hanmenu.notification.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** Feed 的派生查询与必要事务锁. */
interface FeedRecords extends JpaRepository<FeedEntity, Integer> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<FeedEntity> findLockedById(Integer id);
}
