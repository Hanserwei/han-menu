package com.hanserwei.hanmenu.customer.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 顾客账号聚合，封装档案、启停用和会话撤销版本. */
public final class CustomerAccount {
  private final UUID id;
  private final String phone;
  private String displayName;
  private String passwordHash;
  private boolean enabled;
  private long securityVersion;
  private final long version;
  private final Instant createdAt;
  private Instant updatedAt;

  private CustomerAccount(
      UUID id,
      String phone,
      String displayName,
      String passwordHash,
      boolean enabled,
      long securityVersion,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id);
    this.phone = normalizePhone(phone);
    this.displayName = normalizeName(displayName);
    this.passwordHash = Objects.requireNonNull(passwordHash);
    this.enabled = enabled;
    this.securityVersion = securityVersion;
    this.version = version;
    this.createdAt = Objects.requireNonNull(createdAt);
    this.updatedAt = Objects.requireNonNull(updatedAt);
    if (securityVersion < 0 || version < 0) {
      throw new IllegalArgumentException("账号版本不可为负");
    }
  }

  /** 创建已启用顾客账号. */
  public static CustomerAccount create(
      UUID id, String phone, String displayName, String passwordHash, Instant now) {
    return new CustomerAccount(id, phone, displayName, passwordHash, true, 0, 0, now, now);
  }

  /** 从 JPA 快照重建顾客聚合. */
  public static CustomerAccount restore(
      UUID id,
      String phone,
      String displayName,
      String passwordHash,
      boolean enabled,
      long securityVersion,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    return new CustomerAccount(
        id,
        phone,
        displayName,
        passwordHash,
        enabled,
        securityVersion,
        version,
        createdAt,
        updatedAt);
  }

  /** 修改显示名称，不修改顾客登录标识. */
  public void reviseProfile(String replacement, Instant now) {
    displayName = normalizeName(replacement);
    updatedAt = Objects.requireNonNull(now);
  }

  /** 更换密码摘要并撤销全部旧顾客会话. */
  public void changePassword(String replacement, Instant now) {
    passwordHash = Objects.requireNonNull(replacement);
    securityVersion++;
    updatedAt = Objects.requireNonNull(now);
  }

  /** 启停用顾客并撤销旧会话. */
  public void changeEnabled(boolean replacement, Instant now) {
    if (enabled != replacement) {
      enabled = replacement;
      securityVersion++;
      updatedAt = Objects.requireNonNull(now);
    }
  }

  /** 检查认证快照是否仍然有效. */
  public void requireActive(long expectedSecurityVersion) {
    if (!enabled || securityVersion != expectedSecurityVersion) {
      throw new CustomerException(CustomerException.Reason.INVALID_CREDENTIALS, "登录状态已失效");
    }
  }

  /** 检查资源更新版本. */
  public void requireVersion(long expected) {
    if (version != expected) {
      throw new CustomerException(CustomerException.Reason.VERSION_CONFLICT, "顾客资料已被修改");
    }
  }

  /** 规范化带可选加号的数字登录标识，不推断国家区号，也不代表已验证号码归属. */
  public static String normalizePhone(String value) {
    String normalized = Objects.requireNonNull(value, "手机号不能为空").strip();
    if (!normalized.matches("\\+?[1-9][0-9]{6,14}")) {
      throw new CustomerException(CustomerException.Reason.INVALID_INPUT, "手机号格式不正确");
    }
    return normalized.startsWith("+") ? normalized : "+" + normalized;
  }

  private static String normalizeName(String value) {
    String normalized = Objects.requireNonNull(value, "昵称不能为空").strip();
    if (normalized.isBlank() || normalized.length() > 50) {
      throw new CustomerException(CustomerException.Reason.INVALID_INPUT, "昵称不能为空且不能超过 50 个字符");
    }
    return normalized;
  }

  /** 返回聚合标识. */
  public UUID id() {
    return id;
  }

  /** 返回登录手机号. */
  public String phone() {
    return phone;
  }

  /** 返回昵称. */
  public String displayName() {
    return displayName;
  }

  /** 返回密码摘要，仅认证端口使用. */
  public String passwordHash() {
    return passwordHash;
  }

  /** 返回是否启用. */
  public boolean enabled() {
    return enabled;
  }

  /** 返回会话撤销版本. */
  public long securityVersion() {
    return securityVersion;
  }

  /** 返回持久化版本. */
  public long version() {
    return version;
  }

  /** 返回创建时刻. */
  public Instant createdAt() {
    return createdAt;
  }

  /** 返回最后更新时间. */
  public Instant updatedAt() {
    return updatedAt;
  }

  /** 调试输出不暴露手机号或密码. */
  @Override
  public String toString() {
    return "CustomerAccount[id=" + id + "]";
  }
}
