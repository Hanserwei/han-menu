package com.hanserwei.hanmenu.customer.infrastructure.persistence;

import com.hanserwei.hanmenu.customer.domain.CustomerAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** 顾客账号 JPA 实体，手机号是新系统的唯一登录标识. */
@Entity(name = "CustomerAccount")
@Table(name = "customer_account")
public class CustomerEntity {
  @Id UUID id;

  @Column(nullable = false, unique = true, length = 16)
  String phone;

  @Column(nullable = false, length = 50)
  String displayName;

  @Column(nullable = false, length = 100)
  String passwordHash;

  @Column(nullable = false)
  boolean enabled;

  @Column(nullable = false)
  long securityVersion;

  @Version Long version;

  @Column(nullable = false, updatable = false)
  Instant createdAt;

  @Column(nullable = false)
  Instant updatedAt;

  /** 供 ORM 创建空实例，业务通过聚合约束构造. */
  protected CustomerEntity() {}

  static CustomerEntity create(CustomerAccount value) {
    var entity = new CustomerEntity();
    entity.id = value.id();
    entity.createdAt = value.createdAt();
    entity.apply(value);
    return entity;
  }

  void apply(CustomerAccount value) {
    phone = value.phone();
    displayName = value.displayName();
    passwordHash = value.passwordHash();
    enabled = value.enabled();
    securityVersion = value.securityVersion();
    updatedAt = value.updatedAt();
  }

  CustomerAccount domain() {
    return CustomerAccount.restore(
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
}
