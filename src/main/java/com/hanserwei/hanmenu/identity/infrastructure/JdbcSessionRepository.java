package com.hanserwei.hanmenu.identity.infrastructure;

import com.hanserwei.hanmenu.identity.domain.EmployeeAccount;
import com.hanserwei.hanmenu.identity.domain.SessionRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 会话适配器在每次认证时关联最新账号状态，撤销不依赖缓存 TTL. */
@Repository
class JdbcSessionRepository implements SessionRepository {
  private final JdbcClient jdbc;

  JdbcSessionRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void add(String tokenHash, long employeeId, long securityVersion, Instant expiresAt) {
    jdbc.sql(
            """
            INSERT INTO identity_session (token_hash, employee_id, security_version, expires_at)
            VALUES (?, ?, ?, ?)
            """)
        .params(tokenHash, employeeId, securityVersion, Timestamp.from(expiresAt))
        .update();
  }

  @Override
  public Optional<EmployeeAccount> findActive(String tokenHash, Instant now) {
    return jdbc.sql(
            """
            SELECT e.* FROM identity_employee e JOIN identity_session s ON s.employee_id = e.id
            WHERE s.token_hash = ? AND s.expires_at > ? AND e.enabled
              AND s.security_version = e.security_version
            """)
        .params(tokenHash, Timestamp.from(now))
        .query(JdbcEmployeeRepository::map)
        .optional();
  }

  @Override
  public void revoke(String tokenHash) {
    jdbc.sql("DELETE FROM identity_session WHERE token_hash = ?").param(tokenHash).update();
  }

  @Override
  public void deleteExpired(Instant now) {
    jdbc.sql("DELETE FROM identity_session WHERE expires_at <= ?")
        .param(Timestamp.from(now))
        .update();
  }
}
