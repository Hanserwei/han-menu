package com.hanserwei.hanmenu.customer.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** 顾客会话实体只保存令牌摘要，并通过实体图读取账号状态. */
@Entity(name = "CustomerSession")
@Table(name = "customer_session")
public class CustomerSessionEntity {
  @Id String tokenHash;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "customer_id", nullable = false)
  CustomerEntity customer;

  long securityVersion;
  Instant expiresAt;

  /** 供 ORM 创建空实例，业务通过聚合约束构造. */
  protected CustomerSessionEntity() {}

  CustomerSessionEntity(
      String tokenHash, CustomerEntity customer, long securityVersion, Instant expiresAt) {
    this.tokenHash = tokenHash;
    this.customer = customer;
    this.securityVersion = securityVersion;
    this.expiresAt = expiresAt;
  }

  boolean matchesSecurityVersion() {
    return securityVersion == customer.securityVersion;
  }
}
