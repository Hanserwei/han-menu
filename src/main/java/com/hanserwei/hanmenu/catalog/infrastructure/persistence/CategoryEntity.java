package com.hanserwei.hanmenu.catalog.infrastructure.persistence;

import com.hanserwei.hanmenu.catalog.domain.Category;
import com.hanserwei.hanmenu.catalog.domain.ProductKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

/** 分类的持久化映射，业务行为由分类聚合执行. */
@Entity(name = "CatalogCategory")
@Table(name = "catalog_category")
public class CategoryEntity {
  @Id UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  ProductKind kind;

  @Column(nullable = false, length = 50)
  String name;

  @Column(nullable = false)
  int sortOrder;

  @Column(nullable = false)
  boolean enabled;

  @Version Long version;

  /** ORM 构造入口. */
  protected CategoryEntity() {}

  void apply(Category value) {
    id = value.id();
    kind = value.kind();
    name = value.name();
    sortOrder = value.sortOrder();
    enabled = value.enabled();
  }

  Category domain() {
    return new Category(id, kind, name, sortOrder, enabled, version);
  }
}
