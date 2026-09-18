package com.hanserwei.hanmenu.cart.infrastructure.persistence;

import com.hanserwei.hanmenu.cart.domain.CartItem;
import com.hanserwei.hanmenu.cart.domain.ShoppingCart;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

/** 购物车实体，条目通过级联保存，领域对象仍负责数量和合并规则. */
@Entity(name = "ShoppingCart")
@Table(name = "cart")
public class CartEntity {
  @Id UUID customerId;
  @Version Long version;

  @Column(nullable = false)
  Instant updatedAt;

  @OneToMany(
      mappedBy = "cart",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("position ASC")
  java.util.List<CartItemEntity> items = new ArrayList<>();

  /** ORM 重建入口，业务行为由聚合执行. */
  protected CartEntity() {}

  static CartEntity create(ShoppingCart cart) {
    var entity = new CartEntity();
    entity.customerId = cart.customerId();
    entity.apply(cart);
    return entity;
  }

  void apply(ShoppingCart cart) {
    updatedAt = cart.updatedAt();
    var desired =
        cart.items().stream().map(CartItem::id).collect(java.util.stream.Collectors.toSet());
    items.removeIf(item -> !desired.contains(item.id));
    for (var value : cart.items()) {
      var existing = items.stream().filter(item -> item.id.equals(value.id())).findFirst();
      if (existing.isPresent()) {
        existing.orElseThrow().apply(value);
      } else {
        items.add(CartItemEntity.create(this, value));
      }
    }
    for (int position = 0; position < items.size(); position++) {
      items.get(position).position = position;
    }
  }

  ShoppingCart domain() {
    return new ShoppingCart(
        customerId, items.stream().map(CartItemEntity::domain).toList(), version, updatedAt);
  }
}
