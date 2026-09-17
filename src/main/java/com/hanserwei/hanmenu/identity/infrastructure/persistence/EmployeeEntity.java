package com.hanserwei.hanmenu.identity.infrastructure.persistence;

import com.hanserwei.hanmenu.identity.domain.EmployeeAccount;
import com.hanserwei.hanmenu.identity.domain.EmployeeProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** 员工持久化实体，只负责存储映射；业务状态变化由领域聚合执行. */
@Entity(name = "IdentityEmployee")
@Table(name = "identity_employee")
public class EmployeeEntity {
  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 32)
  private String username;

  @Column(nullable = false, length = 50)
  private String displayName;

  @Column(nullable = false, length = 16)
  private String phone;

  @Column(nullable = false, length = 100)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private EmployeeAccount.Role role;

  @Column(nullable = false)
  private boolean enabled;

  @Column(nullable = false)
  private long securityVersion;

  @Version private Long version;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  /** 供 JPA 重建实体；应用用例不能通过空实体绕过聚合约束. */
  protected EmployeeEntity() {}

  static EmployeeEntity create(EmployeeAccount account) {
    var entity = new EmployeeEntity();
    entity.id = account.id();
    entity.role = account.role();
    entity.createdAt = account.createdAt();
    entity.apply(account);
    // 新实体的包装类型版本保持 null，Spring Data 因此执行 persist 而不是 merge。
    return entity;
  }

  void apply(EmployeeAccount account) {
    username = account.profile().username();
    displayName = account.profile().displayName();
    phone = account.profile().phone();
    passwordHash = account.passwordHash();
    enabled = account.enabled();
    securityVersion = account.securityVersion();
    updatedAt = account.updatedAt();
  }

  EmployeeAccount toDomain() {
    return EmployeeAccount.restore(
        id,
        new EmployeeProfile(username, displayName, phone),
        passwordHash,
        role,
        enabled,
        securityVersion,
        version,
        createdAt,
        updatedAt);
  }

  long version() {
    return version;
  }

  long securityVersion() {
    return securityVersion;
  }
}
