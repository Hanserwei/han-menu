package com.hanserwei.hanmenu.customer.infrastructure.persistence;

import com.hanserwei.hanmenu.customer.domain.DeliveryAddress;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** 收货地址 JPA 实体，customerId 是归属边界的一部分. */
@Entity(name = "CustomerAddress")
@Table(name = "customer_address")
public class AddressEntity {
  @Id UUID id;

  @Column(nullable = false)
  UUID customerId;

  @Column(nullable = false, length = 20)
  String label;

  @Column(nullable = false, length = 50)
  String recipientName;

  @Column(nullable = false, length = 16)
  String phone;

  @Column(nullable = false, length = 50)
  String province;

  @Column(nullable = false, length = 50)
  String city;

  @Column(nullable = false, length = 50)
  String district;

  @Column(nullable = false, length = 200)
  String detail;

  @Column(name = "is_default", nullable = false)
  boolean defaultAddress;

  @Version Long version;

  @Column(nullable = false, updatable = false)
  Instant createdAt;

  @Column(nullable = false)
  Instant updatedAt;

  /** 供 ORM 创建空实例，业务通过聚合约束构造. */
  protected AddressEntity() {}

  static AddressEntity create(DeliveryAddress value) {
    var entity = new AddressEntity();
    entity.id = value.id();
    entity.customerId = value.customerId();
    entity.createdAt = value.createdAt();
    entity.apply(value);
    return entity;
  }

  void apply(DeliveryAddress value) {
    label = value.label();
    recipientName = value.recipientName();
    phone = value.phone();
    province = value.province();
    city = value.city();
    district = value.district();
    detail = value.detail();
    defaultAddress = value.defaultAddress();
    updatedAt = value.updatedAt();
  }

  DeliveryAddress domain() {
    return new DeliveryAddress(
        id,
        customerId,
        label,
        recipientName,
        phone,
        province,
        city,
        district,
        detail,
        defaultAddress,
        version,
        createdAt,
        updatedAt);
  }
}
