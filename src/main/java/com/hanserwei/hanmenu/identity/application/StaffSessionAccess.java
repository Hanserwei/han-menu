package com.hanserwei.hanmenu.identity.application;

import com.hanserwei.hanmenu.identity.api.StaffSessions;
import com.hanserwei.hanmenu.identity.domain.SessionRepository;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 以数据库会话为准验证长连接身份，不把握手时的权限永久缓存. */
@Service
@Transactional(readOnly = true)
class StaffSessionAccess implements StaffSessions {
  private final EmployeeAuthentication authentication;
  private final SessionRepository sessions;
  private final Clock clock;

  StaffSessionAccess(
      EmployeeAuthentication authentication, SessionRepository sessions, Clock clock) {
    this.authentication = authentication;
    this.sessions = sessions;
    this.clock = clock;
  }

  @Override
  public Optional<Proof> authenticate(String accessToken) {
    return authentication
        .authenticate(accessToken)
        .map(
            value ->
                new Proof(
                    value.identity().employeeId(),
                    value.identity().securityVersion(),
                    value.tokenHash()));
  }

  @Override
  public boolean active(Proof proof) {
    return proof != null
        && sessions
            .findActive(proof.sessionHash(), clock.instant())
            .filter(
                account ->
                    account.id().equals(proof.employeeId())
                        && account.securityVersion() == proof.securityVersion())
            .isPresent();
  }
}
