package com.hanserwei.hanmenu.catalog.infrastructure.persistence;

import com.hanserwei.hanmenu.catalog.domain.FlavorGroup;
import com.hanserwei.hanmenu.catalog.domain.MenuProduct;
import com.hanserwei.hanmenu.catalog.domain.Money;
import com.hanserwei.hanmenu.catalog.domain.ProductKind;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 商品实体映射；口味作为小型 JSON 值集合，套餐组成使用有外键约束的关联表. */
@Entity(name = "CatalogProduct")
@Table(name = "catalog_product")
public class ProductEntity {
  @Id UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  ProductKind kind;

  @Column(nullable = false)
  UUID categoryId;

  @Column(nullable = false, length = 100)
  String name;

  @Column(nullable = false, length = 1000)
  String description;

  @Column(nullable = false, precision = 8, scale = 2)
  BigDecimal price;

  UUID imageId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  List<FlavorGroup> flavors;

  @ElementCollection
  @CollectionTable(name = "catalog_meal_component", joinColumns = @JoinColumn(name = "meal_id"))
  @OrderColumn(name = "position")
  List<ComponentValue> components = new ArrayList<>();

  @Column(nullable = false)
  boolean onSale;

  @Version Long version;

  @Column(nullable = false, updatable = false)
  Instant createdAt;

  /** ORM 构造入口. */
  protected ProductEntity() {}

  void apply(MenuProduct product) {
    id = product.id();
    kind = product.kind();
    categoryId = product.categoryId();
    name = product.name();
    description = product.description();
    price = product.price().amount();
    imageId = product.imageId();
    flavors = product.flavors();
    components.clear();
    product.components().stream().map(ComponentValue::from).forEach(components::add);
    onSale = product.onSale();
    createdAt = product.createdAt();
  }

  MenuProduct domain() {
    return new MenuProduct(
        id,
        kind,
        categoryId,
        name,
        description,
        new Money(price),
        imageId,
        flavors,
        components.stream().map(ComponentValue::domain).toList(),
        onSale,
        version,
        createdAt);
  }
}
