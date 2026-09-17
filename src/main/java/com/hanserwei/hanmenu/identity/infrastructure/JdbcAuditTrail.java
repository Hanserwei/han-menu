package com.hanserwei.hanmenu.identity.infrastructure;

import com.hanserwei.hanmenu.identity.domain.AuditTrail;
import java.sql.Timestamp;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 以最小字段保存审计；诊断日志也只包含固定枚举及内部编号，不接收用户输入文本. */
@Repository
class JdbcAuditTrail implements AuditTrail {
  private static final Logger LOGGER = LoggerFactory.getLogger("hanmenu.security.audit");
  private final JdbcClient jdbc;
  private final Clock clock;

  JdbcAuditTrail(JdbcClient jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Override
  public void record(Action action, Long actorId, Long subjectId, boolean success) {
    String outcome = success ? "SUCCESS" : "FAILURE";
    jdbc.sql(
            """
            INSERT INTO identity_audit (action, outcome, actor_id, subject_id, occurred_at)
            VALUES (:action, :outcome, :actor, :subject, :time)
            """)
        .param("action", action.name())
        .param("outcome", outcome)
        .param("actor", actorId)
        .param("subject", subjectId)
        .param("time", Timestamp.from(clock.instant()))
        .update();
    // 成功事务可能尚未提交，因此日志描述记录动作，数据库内的事务性记录才是审计依据。
    LOGGER.info(
        "security_audit action={} outcome={} actorId={} subjectId={}",
        action,
        outcome,
        actorId,
        subjectId);
  }
}
