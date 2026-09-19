package com.hanserwei.hanmenu.notification.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** Ticket 的派生查询与必要事务锁. */
interface TicketRecords extends JpaRepository<TicketEntity, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<TicketEntity> findLockedByTicketHash(String hash);

  long deleteByExpiresAtLessThanEqual(Instant now);
}
