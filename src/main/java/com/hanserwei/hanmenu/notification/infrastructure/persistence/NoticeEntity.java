package com.hanserwei.hanmenu.notification.infrastructure.persistence;

import com.hanserwei.hanmenu.notification.domain.Notice;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** 通知实体与业务聚合分离，持久化提交游标和投递状态. */
@Entity(name = "NotificationNotice")
@Table(name = "notification_notice")
public class NoticeEntity {
  @Id UUID id;
  long sequence;
  UUID orderId;

  @Enumerated(EnumType.STRING)
  Notice.Kind kind;

  Instant occurredAt;
  Instant createdAt;

  @Enumerated(EnumType.STRING)
  Notice.Status status;

  int attempts;
  int failures;
  Instant nextAttemptAt;
  UUID activeAttemptId;
  String lastFailure;
  @Version Long version;

  /** ORM 重建入口. */
  protected NoticeEntity() {}

  static NoticeEntity from(Notice value) {
    var entity = new NoticeEntity();
    entity.id = value.id();
    entity.sequence = value.sequence();
    entity.orderId = value.orderId();
    entity.kind = value.kind();
    entity.occurredAt = value.occurredAt();
    entity.createdAt = value.createdAt();
    entity.apply(value);
    return entity;
  }

  void apply(Notice value) {
    status = value.status();
    attempts = value.attempts();
    failures = value.failures();
    nextAttemptAt = value.nextAttemptAt();
    activeAttemptId = value.activeAttemptId();
    lastFailure = value.lastFailure();
  }

  Notice domain() {
    return new Notice(
        id,
        sequence,
        orderId,
        kind,
        occurredAt,
        createdAt,
        status,
        attempts,
        failures,
        nextAttemptAt,
        activeAttemptId,
        lastFailure,
        version);
  }
}
