package com.hanserwei.hanmenu.customer.application;

import com.hanserwei.hanmenu.customer.api.CustomerIdentity;
import com.hanserwei.hanmenu.customer.domain.CustomerAccount;
import com.hanserwei.hanmenu.customer.domain.CustomerAttemptLimiter;
import com.hanserwei.hanmenu.customer.domain.CustomerException;
import com.hanserwei.hanmenu.customer.domain.CustomerPassword;
import com.hanserwei.hanmenu.customer.domain.CustomerPasswordHasher;
import com.hanserwei.hanmenu.customer.domain.CustomerRepository;
import com.hanserwei.hanmenu.customer.domain.CustomerSessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 顾客注册、登录、会话认证和退出用例，完全独立于员工身份模块. */
@Service
public class CustomerAuthentication {
  private final CustomerRepository customers;
  private final CustomerPasswordHasher passwords;
  private final CustomerSessionRepository sessions;
  private final CustomerTokenFactory tokens;
  private final Clock clock;
  private final Duration ttl;
  private final CustomerAttemptLimiter limiter;

  /** 通过领域端口组合顾客认证，不引入微信或短信供应商. */
  public CustomerAuthentication(
      CustomerRepository customers,
      CustomerPasswordHasher passwords,
      CustomerSessionRepository sessions,
      CustomerTokenFactory tokens,
      CustomerAttemptLimiter limiter,
      Clock clock,
      @Value("${han-menu.customer.session-ttl}") Duration ttl) {
    if (ttl.isZero() || ttl.isNegative() || ttl.compareTo(Duration.ofDays(30)) > 0) {
      throw new IllegalArgumentException("顾客会话有效期必须在 30 天以内");
    }
    this.customers = customers;
    this.passwords = passwords;
    this.sessions = sessions;
    this.tokens = tokens;
    this.clock = clock;
    this.ttl = ttl;
    this.limiter = limiter;
  }

  /** 注册顾客，手机号唯一，密码摘要只保存在服务端. */
  @Transactional
  public CustomerAccount register(
      String phone, String displayName, CustomerPassword password, String clientAddress) {
    String normalized = CustomerAccount.normalizePhone(phone);
    limiter.check(normalized, clientAddress);
    if (customers.findByPhone(normalized).isPresent()) {
      throw new CustomerException(CustomerException.Reason.CONFLICT, "手机号已注册");
    }
    var customer =
        CustomerAccount.create(
            java.util.UUID.randomUUID(),
            normalized,
            displayName,
            passwords.encode(password),
            clock.instant());
    customers.add(customer);
    return customer;
  }

  /** 登录成功后创建顾客会话；错误账号与错误密码使用统一消息. */
  @Transactional
  public LoginResult login(String phone, String rawPassword, String clientAddress) {
    String normalized = CustomerAccount.normalizePhone(phone);
    limiter.check(normalized, clientAddress);
    var found = customers.findByPhone(normalized);
    boolean matched =
        passwords.matches(rawPassword, found.map(CustomerAccount::passwordHash).orElse(null));
    if (found.isEmpty() || !matched || !found.orElseThrow().enabled()) {
      throw new CustomerException(CustomerException.Reason.INVALID_CREDENTIALS, "手机号或密码错误");
    }
    var customer = found.orElseThrow();
    String token = tokens.newToken();
    Instant expiresAt = clock.instant().plus(ttl);
    sessions.deleteExpired(clock.instant());
    sessions.add(tokens.digest(token), customer.id(), customer.securityVersion(), expiresAt);
    return new LoginResult(identity(customer), token, expiresAt);
  }

  /** 认证顾客 Bearer 令牌，检查数据库状态与安全版本. */
  @Transactional(readOnly = true)
  public Optional<AuthenticatedSession> authenticate(String token) {
    if (token == null || !token.matches("hmc_[A-Za-z0-9_-]{43}")) {
      return Optional.empty();
    }
    return sessions
        .findActive(tokens.digest(token), clock.instant())
        .map(customer -> new AuthenticatedSession(identity(customer), tokens.digest(token)));
  }

  /** 撤销当前顾客会话. */
  @Transactional
  public void logout(String tokenHash) {
    sessions.revoke(tokenHash);
  }

  /** 修改顾客密码并撤销该账号全部旧会话. */
  @Transactional
  public void changePassword(
      CustomerIdentity identity, String current, CustomerPassword replacement) {
    var customer = customer(identity.customerId());
    customer.requireActive(identity.securityVersion());
    if (!passwords.matches(current, customer.passwordHash())) {
      throw new CustomerException(CustomerException.Reason.INVALID_CREDENTIALS, "当前密码不正确");
    }
    customer.changePassword(passwords.encode(replacement), clock.instant());
    customers.update(customer);
  }

  /** 修改当前顾客昵称，必须携带当前版本. */
  @Transactional
  public CustomerAccount reviseProfile(
      CustomerIdentity identity, String displayName, long version) {
    var customer = customer(identity.customerId());
    customer.requireActive(identity.securityVersion());
    customer.requireVersion(version);
    customer.reviseProfile(displayName, clock.instant());
    customers.update(customer);
    return customer(identity.customerId());
  }

  /** 获取当前顾客聚合. */
  @Transactional(readOnly = true)
  public CustomerAccount current(CustomerIdentity identity) {
    if (identity == null) {
      throw new CustomerException(CustomerException.Reason.INVALID_CREDENTIALS, "请先登录顾客账号");
    }
    var customer = customer(identity.customerId());
    customer.requireActive(identity.securityVersion());
    return customer;
  }

  private CustomerAccount customer(java.util.UUID id) {
    return customers
        .findById(id)
        .orElseThrow(
            () -> new CustomerException(CustomerException.Reason.INVALID_CREDENTIALS, "顾客身份已失效"));
  }

  private CustomerIdentity identity(CustomerAccount customer) {
    return new CustomerIdentity(
        customer.id(), customer.phone(), customer.displayName(), customer.securityVersion());
  }

  /** 顾客登录响应，令牌不允许出现在日志或默认字符串中. */
  public record LoginResult(CustomerIdentity identity, String accessToken, Instant expiresAt) {
    @Override
    public String toString() {
      return "LoginResult[令牌已隐藏]";
    }
  }

  /** 安全链内部的认证快照，令牌摘要不返回客户端. */
  public record AuthenticatedSession(CustomerIdentity identity, String tokenHash) {
    @Override
    public String toString() {
      return "AuthenticatedSession[会话已隐藏]";
    }
  }
}
