package com.hanserwei.hanmenu.notification.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Attempt 的通知模块内部持久化模型. */
@Entity(name = "NotificationAttempt")
@Table(name = "notification_attempt")
public class AttemptEntity {
  @Id UUID id;
  UUID noticeId;
  Instant startedAt;
  Instant finishedAt;
  String status;
  int sent;
  int failed;
  String failure;

  /** ORM 重建入口. */
  protected AttemptEntity() {}
}
