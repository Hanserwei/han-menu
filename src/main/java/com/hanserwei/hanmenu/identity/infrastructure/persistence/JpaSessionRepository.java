package com.hanserwei.hanmenu.identity.infrastructure.persistence;

import com.hanserwei.hanmenu.identity.domain.EmployeeAccount;
import com.hanserwei.hanmenu.identity.domain.SessionRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.domain.PredicateSpecification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 通过 ORM 管理会话；安全版本检查在已抓取的同一账号快照上完成. */
@Repository
@Transactional
class JpaSessionRepository implements SessionRepository {
  private final SessionRecords sessions;
  private final EmployeeRecords employees;

  JpaSessionRepository(SessionRecords sessions, EmployeeRecords employees) {
    this.sessions = sessions;
    this.employees = employees;
  }

  @Override
  public void add(String tokenHash, UUID employeeId, long securityVersion, Instant expiresAt) {
    sessions.saveAndFlush(
        new SessionEntity(
            tokenHash, employees.getReferenceById(employeeId), securityVersion, expiresAt));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<EmployeeAccount> findActive(String tokenHash, Instant now) {
    return sessions
        .findByTokenHashAndExpiresAtAfterAndEmployeeEnabledTrue(tokenHash, now)
        .filter(SessionEntity::matchesSecurityVersion)
        .map(session -> session.employee().toDomain());
  }

  @Override
  public void revoke(String tokenHash) {
    sessions.deleteById(tokenHash);
  }

  @Override
  public void deleteExpired(Instant now) {
    // Criteria 批量删除不加载全部到期会话，数据库语句由 ORM 生成。
    PredicateSpecification<SessionEntity> expired =
        (root, builder) -> builder.lessThanOrEqualTo(root.get("expiresAt"), now);
    sessions.delete(expired);
  }
}
