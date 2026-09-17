package com.hanserwei.hanmenu.identity.infrastructure.persistence;

import com.hanserwei.hanmenu.identity.domain.AuditTrail;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 审计持久化参与当前用例事务；日志仅记录固定动作、结果和内部编号. */
@Repository
@Transactional
class JpaAuditTrail implements AuditTrail {
  private static final Logger LOGGER = LoggerFactory.getLogger("hanmenu.security.audit");
  private final AuditRecords records;
  private final Clock clock;

  JpaAuditTrail(AuditRecords records, Clock clock) {
    this.records = records;
    this.clock = clock;
  }

  @Override
  public void record(Action action, UUID actorId, UUID subjectId, boolean success) {
    records.save(new AuditEntity(action, actorId, subjectId, success, clock.instant()));
    LOGGER.info(
        "security_audit action={} success={} actorId={} subjectId={}",
        action,
        success,
        actorId,
        subjectId);
  }
}
