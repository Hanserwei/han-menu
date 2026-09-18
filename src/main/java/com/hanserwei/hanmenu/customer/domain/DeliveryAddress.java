package com.hanserwei.hanmenu.customer.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 顾客收货地址实体，归属于一个顾客并封装默认地址状态. */
public final class DeliveryAddress {
  private final UUID id;
  private final UUID customerId;
  private String label;
  private String recipientName;
  private String phone;
  private String province;
  private String city;
  private String district;
  private String detail;
  private boolean defaultAddress;
  private final long version;
  private final Instant createdAt;
  private Instant updatedAt;

  /** 从持久化快照重建地址实体. */
  public DeliveryAddress(
      UUID id,
      UUID customerId,
      String label,
      String recipientName,
      String phone,
      String province,
      String city,
      String district,
      String detail,
      boolean defaultAddress,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id);
    this.customerId = Objects.requireNonNull(customerId);
    this.createdAt = Objects.requireNonNull(createdAt);
    if (version < 0) {
      throw new IllegalArgumentException("地址版本不可为负");
    }
    this.version = version;
    revise(label, recipientName, phone, province, city, district, detail, updatedAt);
    this.defaultAddress = defaultAddress;
  }

  /** 修改地址内容，不改变归属顾客. */
  public void revise(
      String label,
      String recipientName,
      String phone,
      String province,
      String city,
      String district,
      String detail,
      Instant now) {
    this.label = text(label, 20, "地址标签");
    this.recipientName = text(recipientName, 50, "收货人");
    this.phone = CustomerAccount.normalizePhone(phone);
    this.province = text(province, 50, "省份");
    this.city = text(city, 50, "城市");
    this.district = text(district, 50, "区县");
    this.detail = text(detail, 200, "详细地址");
    this.updatedAt = Objects.requireNonNull(now);
  }

  /** 标记为默认地址；取消其他默认地址由应用服务在同一事务完成. */
  public void makeDefault(Instant now) {
    defaultAddress = true;
    updatedAt = Objects.requireNonNull(now);
  }

  /** 取消默认状态，供地址簿切换默认地址使用. */
  public void clearDefault(Instant now) {
    defaultAddress = false;
    updatedAt = Objects.requireNonNull(now);
  }

  /** 检查陈旧编辑. */
  public void requireVersion(long expected) {
    if (version != expected) {
      throw new CustomerException(CustomerException.Reason.VERSION_CONFLICT, "地址已被修改");
    }
  }

  private static String text(String value, int max, String label) {
    String normalized = Objects.requireNonNull(value, label + "不能为空").strip();
    if (normalized.isBlank() || normalized.length() > max) {
      throw new CustomerException(CustomerException.Reason.INVALID_INPUT, label + "不能为空且不能超长");
    }
    return normalized;
  }

  /** 调试输出不包含收货人、地址或手机号. */
  @Override
  public String toString() {
    return "DeliveryAddress[id=" + id + "]";
  }

  /** 地址标识. */
  public UUID id() {
    return id;
  }

  /** 所属顾客标识. */
  public UUID customerId() {
    return customerId;
  }

  /** 地址标签. */
  public String label() {
    return label;
  }

  /** 收货人. */
  public String recipientName() {
    return recipientName;
  }

  /** 收货手机号. */
  public String phone() {
    return phone;
  }

  /** 省. */
  public String province() {
    return province;
  }

  /** 市. */
  public String city() {
    return city;
  }

  /** 区县. */
  public String district() {
    return district;
  }

  /** 详细地址. */
  public String detail() {
    return detail;
  }

  /** 是否默认. */
  public boolean defaultAddress() {
    return defaultAddress;
  }

  /** 持久化版本. */
  public long version() {
    return version;
  }

  /** 创建时间. */
  public Instant createdAt() {
    return createdAt;
  }

  /** 更新时间. */
  public Instant updatedAt() {
    return updatedAt;
  }
}
