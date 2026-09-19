package com.hanserwei.hanmenu.ordering.domain;

import java.util.Objects;
import java.util.UUID;

/** 不可变收货资料，与地址簿后续修改或删除隔离. */
public record AddressSnapshot(
    UUID sourceId,
    long sourceVersion,
    String recipientName,
    String phone,
    String province,
    String city,
    String district,
    String detail) {
  /** 校验快照完整性，不保存地址簿领域对象引用. */
  public AddressSnapshot {
    Objects.requireNonNull(sourceId);
    if (sourceVersion < 0) {
      throw new IllegalArgumentException("地址版本不可为负");
    }
    for (String value : new String[] {recipientName, phone, province, city, district, detail}) {
      if (value == null || value.isBlank()) {
        throw new IllegalArgumentException("收货快照不完整");
      }
    }
  }

  @Override
  public String toString() {
    return "AddressSnapshot[收货资料已隐藏]";
  }
}
