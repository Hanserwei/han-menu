package com.hanserwei.hanmenu.identity.infrastructure.persistence;

import com.hanserwei.hanmenu.identity.domain.AuditQuery;
import com.hanserwei.hanmenu.identity.domain.AuditTrail;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 最小化审计持久化对象，不包含凭证、请求体或个人资料字段. */
@Entity(name = "IdentityAudit")
@Table(name = "identity_audit")
public class AuditEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private AuditTrail.Action action;

  @Column(nullable = false)
  private boolean successful;

  private UUID actorId;
  private UUID subjectId;

  @Column(nullable = false)
  private Instant occurredAt;

  /** 供 ORM 重建审计实体. */
  protected AuditEntity() {}

  AuditEntity(
      AuditTrail.Action action,
      UUID actorId,
      UUID subjectId,
      boolean successful,
      Instant occurredAt) {
    this.action = action;
    this.actorId = actorId;
    this.subjectId = subjectId;
    this.successful = successful;
    this.occurredAt = occurredAt;
  }

  /** 仅映射固定审计字段，不附加员工或顾客个人资料. */
  AuditQuery.Entry entry() {
    return new AuditQuery.Entry(id, action, actorId, subjectId, successful, occurredAt);
  }
}
