package com.hanserwei.hanmenu.notification.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Ticket 的通知模块内部持久化模型. */
@Entity(name = "NotificationTicket")
@Table(name = "notification_ticket")
public class TicketEntity {
  @Id String sessionHash;
  String ticketHash;
  UUID employeeId;
  long securityVersion;
  Instant expiresAt;
  Instant consumedAt;
  @jakarta.persistence.Version Long version;

  /** ORM 重建入口. */
  protected TicketEntity() {}
}
