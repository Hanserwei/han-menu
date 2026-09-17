package com.hanserwei.hanmenu.identity.application;

import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import com.hanserwei.hanmenu.identity.domain.AuditTrail;
import com.hanserwei.hanmenu.identity.domain.EmployeeAccount;
import com.hanserwei.hanmenu.identity.domain.EmployeeProfile;
import com.hanserwei.hanmenu.identity.domain.EmployeeRepository;
import com.hanserwei.hanmenu.identity.domain.IdentityException;
import com.hanserwei.hanmenu.identity.domain.LoginAttemptLimiter;
import com.hanserwei.hanmenu.identity.domain.PasswordHasher;
import com.hanserwei.hanmenu.identity.domain.SessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理会话创建、认证与撤销，原始令牌仅在创建会话时交给调用方. */
@Service
public class EmployeeAuthentication {
  private final EmployeeRepository employees;
  private final PasswordHasher passwords;
  private final SessionRepository sessions;
  private final LoginAttemptLimiter limiter;
  private final AuditTrail audit;
  private final TokenFactory tokens;
  private final Clock clock;
  private final Duration ttl;

  /** 组合账号、密码、会话和限流端口；令牌有效期必须为正数且不超过一天. */
  public EmployeeAuthentication(
      EmployeeRepository employees,
      PasswordHasher passwords,
      SessionRepository sessions,
      LoginAttemptLimiter limiter,
      AuditTrail audit,
      TokenFactory tokens,
      Clock clock,
      @Value("${han-menu.identity.session-ttl}") Duration ttl) {
    if (ttl.isZero() || ttl.isNegative() || ttl.compareTo(Duration.ofDays(1)) > 0) {
      throw new IllegalArgumentException("会话有效期必须在一天以内");
    }
    this.employees = employees;
    this.passwords = passwords;
    this.sessions = sessions;
    this.limiter = limiter;
    this.audit = audit;
    this.tokens = tokens;
    this.clock = clock;
    this.ttl = ttl;
  }

  /** 登录失败也需提交失败审计；业务失败发生前不保存会话，数据库异常仍回滚. */
  @Transactional(noRollbackFor = IdentityException.class)
  public LoginResult login(String username, String password, String clientAddress) {
    String normalized = EmployeeProfile.normalizeUsername(username);
    try {
      limiter.check(normalized, clientAddress);
    } catch (IdentityException exception) {
      audit.record(AuditTrail.Action.LOGIN_LIMITED, null, null, false);
      throw exception;
    }
    var found = employees.findByUsername(normalized);
    boolean matches =
        passwords.matches(password, found.map(EmployeeAccount::passwordHash).orElse(null));
    if (!matches || found.isEmpty() || !found.orElseThrow().enabled()) {
      audit.record(AuditTrail.Action.LOGIN, null, null, false);
      throw new IdentityException(IdentityException.Reason.INVALID_CREDENTIALS, "用户名或密码错误");
    }
    var account = found.orElseThrow();
    String token = tokens.newToken();
    Instant expires = clock.instant().plus(ttl);
    sessions.deleteExpired(clock.instant());
    sessions.add(tokens.digest(token), account.id(), account.securityVersion(), expires);
    audit.record(AuditTrail.Action.LOGIN, account.id(), account.id(), true);
    return new LoginResult(identity(account), token, expires);
  }

  /** 查询最新会话状态，不把失效账号缓存为可访问身份. */
  @Transactional(readOnly = true)
  public Optional<AuthenticatedSession> authenticate(String token) {
    if (token == null || !token.matches("hme_[A-Za-z0-9_-]{43}")) {
      return Optional.empty();
    }
    String hash = tokens.digest(token);
    return sessions
        .findActive(hash, clock.instant())
        .map(account -> new AuthenticatedSession(identity(account), hash));
  }

  /** 删除当前令牌摘要；相同账号的其他会话由各自退出或安全版本变更撤销. */
  @Transactional
  public void logout(StaffIdentity actor, String tokenHash) {
    sessions.revoke(tokenHash);
    audit.record(AuditTrail.Action.LOGOUT, actor.employeeId(), actor.employeeId(), true);
  }

  private StaffIdentity identity(EmployeeAccount account) {
    return new StaffIdentity(
        account.id(),
        account.profile().username(),
        account.profile().displayName(),
        account.role().name(),
        account.securityVersion());
  }

  /** 登录输出中的令牌只交给调用方，默认日志表示不包含令牌. */
  public record LoginResult(StaffIdentity identity, String token, Instant expiresAt) {
    @Override
    public String toString() {
      return "LoginResult[token=已隐藏]";
    }
  }

  /** 安全链内部使用的认证结果，摘要不作为公开 HTTP 响应返回. */
  public record AuthenticatedSession(StaffIdentity identity, String tokenHash) {
    @Override
    public String toString() {
      return "AuthenticatedSession[会话已隐藏]";
    }
  }
}
