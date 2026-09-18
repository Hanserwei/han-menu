package com.hanserwei.hanmenu.shop.infrastructure.persistence;

import com.hanserwei.hanmenu.shop.domain.Shop;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** 单店配置实体，数据库迁移创建初始关闭记录. */
@Entity(name = "ShopProfile")
@Table(name = "shop_profile")
public class ShopEntity {
  @Id Integer id;

  @Column(nullable = false, length = 100)
  String name;

  @Column(nullable = false, length = 16)
  String phone;

  @Column(nullable = false, length = 300)
  String address;

  @Column(nullable = false)
  boolean open;

  @Version Long version;

  /** ORM 构造入口. */
  protected ShopEntity() {}

  Shop domain() {
    return new Shop(name, phone, address, open, version);
  }

  void apply(Shop value) {
    name = value.name();
    phone = value.phone();
    address = value.address();
    open = value.open();
  }
}
