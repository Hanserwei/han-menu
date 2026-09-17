package com.hanserwei.hanmenu.identity.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * 员工账号聚合，通过业务方法维护启停用、资料和凭证状态.
 *
 * <p>securityVersion 用于撤销既有会话，version 用于持久化乐观锁，两者职责不同。 管理员角色在当前阶段由初始化流程建立，普通员工接口不能提升角色或停用管理员。
 */
public final class EmployeeAccount {
  private final long id;
  private EmployeeProfile profile;
  private String passwordHash;
  private final Role role;
  private boolean enabled;
  private long securityVersion;
  private final long version;
  private final Instant createdAt;
  private Instant updatedAt;

  private EmployeeAccount(
      long id,
      EmployeeProfile profile,
      String passwordHash,
      Role role,
      boolean enabled,
      long securityVersion,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    if (id <= 0 || version < 0 || securityVersion < 0) {
      throw new IllegalArgumentException("账号标识和版本不合法");
    }
    this.id = id;
    this.profile = Objects.requireNonNull(profile);
    this.passwordHash = Objects.requireNonNull(passwordHash);
    this.role = Objects.requireNonNull(role);
    this.enabled = enabled;
    this.securityVersion = securityVersion;
    this.version = version;
    this.createdAt = Objects.requireNonNull(createdAt);
    this.updatedAt = Objects.requireNonNull(updatedAt);
    if (role == Role.ADMIN && !enabled) {
      throw new IllegalArgumentException("管理员必须保持启用");
    }
  }

  /** 创建启用的员工账号，初始安全版本与业务版本均为零. */
  public static EmployeeAccount create(
      long id, EmployeeProfile profile, String hash, Role role, Instant now) {
    return new EmployeeAccount(id, profile, hash, role, true, 0, 0, now, now);
  }

  /** 从持久化快照重建聚合，同时验证不可破坏的基础状态约束. */
  public static EmployeeAccount restore(
      long id,
      EmployeeProfile profile,
      String hash,
      Role role,
      boolean enabled,
      long securityVersion,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    return new EmployeeAccount(
        id, profile, hash, role, enabled, securityVersion, version, createdAt, updatedAt);
  }

  /** 更新资料，身份标识和权限角色不随资料编辑改变. */
  public void reviseProfile(EmployeeProfile replacement, Instant now) {
    profile = Objects.requireNonNull(replacement);
    updatedAt = Objects.requireNonNull(now);
  }

  /** 启停用员工并撤销旧会话；管理员不允许通过员工管理入口停用. */
  public void changeEnabled(boolean replacement, Instant now) {
    if (role == Role.ADMIN && !replacement) {
      throw new IdentityException(IdentityException.Reason.CONFLICT, "不能停用管理员账号");
    }
    if (enabled != replacement) {
      enabled = replacement;
      securityVersion++;
      updatedAt = Objects.requireNonNull(now);
    }
  }

  /** 更换密码摘要并递增安全版本，使该账号的全部旧会话失效. */
  public void changePassword(String replacement, Instant now) {
    passwordHash = Objects.requireNonNull(replacement);
    securityVersion++;
    updatedAt = Objects.requireNonNull(now);
  }

  /** 校验当前主体仍有效，避免请求进入用例前后的状态变化被忽略. */
  public void requireActive(long expectedSecurityVersion) {
    if (!enabled || securityVersion != expectedSecurityVersion) {
      throw new IdentityException(IdentityException.Reason.INVALID_CREDENTIALS, "登录状态已失效");
    }
  }

  /** 员工管理属于管理员职责，领域规则不依赖 HTTP 路由是否正确配置. */
  public void requireAdministrator() {
    if (!enabled || role != Role.ADMIN) {
      throw new IdentityException(IdentityException.Reason.FORBIDDEN, "没有员工管理权限");
    }
  }

  /** 检查客户端提供的版本；旧客户端未提供版本时仍由仓储执行写入并发检查. */
  public void requireVersion(Long expected) {
    if (expected != null && expected != version) {
      throw new IdentityException(IdentityException.Reason.CONFLICT, "资料已被修改，请刷新后重试");
    }
  }

  /** 返回账号标识. */
  public long id() {
    return id;
  }

  /** 返回不可变档案. */
  public EmployeeProfile profile() {
    return profile;
  }

  /** 返回供凭证端口比较的摘要，接口层不得将其序列化返回. */
  public String passwordHash() {
    return passwordHash;
  }

  /** 返回不可由普通员工接口修改的角色. */
  public Role role() {
    return role;
  }

  /** 返回账号当前是否可登录. */
  public boolean enabled() {
    return enabled;
  }

  /** 返回会话撤销版本. */
  public long securityVersion() {
    return securityVersion;
  }

  /** 返回加载时的业务版本. */
  public long version() {
    return version;
  }

  /** 返回创建时刻. */
  public Instant createdAt() {
    return createdAt;
  }

  /** 返回最后一次业务修改时刻. */
  public Instant updatedAt() {
    return updatedAt;
  }

  /** 当前阶段固定的权限角色，后续可按真实权限模型演化. */
  public enum Role {
    ADMIN,
    STAFF
  }
}
