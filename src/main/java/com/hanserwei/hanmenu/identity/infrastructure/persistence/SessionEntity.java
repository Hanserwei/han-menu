package com.hanserwei.hanmenu.identity.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** 会话只持久化令牌摘要，关联同一模块内的员工以检查即时撤销状态. */
@Entity(name = "IdentitySession")
@Table(name = "identity_session")
public class SessionEntity {
  @Id
  @Column(length = 64)
  private String tokenHash;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "employee_id", nullable = false)
  private EmployeeEntity employee;

  @Column(nullable = false)
  private long securityVersion;

  @Column(nullable = false)
  private Instant expiresAt;

  /** 供 ORM 加载会话，不能作为业务构造入口. */
  protected SessionEntity() {}

  SessionEntity(
      String tokenHash, EmployeeEntity employee, long securityVersion, Instant expiresAt) {
    this.tokenHash = tokenHash;
    this.employee = employee;
    this.securityVersion = securityVersion;
    this.expiresAt = expiresAt;
  }

  EmployeeEntity employee() {
    return employee;
  }

  boolean matchesSecurityVersion() {
    return securityVersion == employee.securityVersion();
  }
}
