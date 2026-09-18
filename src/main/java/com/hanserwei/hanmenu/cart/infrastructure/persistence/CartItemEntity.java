package com.hanserwei.hanmenu.cart.infrastructure.persistence;

import com.hanserwei.hanmenu.cart.domain.CartItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 购物车条目持久化映射，规格选择使用 JSONB 保存结构化值. */
@Entity(name = "CartItem")
@Table(name = "cart_item")
public class CartItemEntity {
  @Id UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "customer_id", nullable = false)
  CartEntity cart;

  @Column(nullable = false)
  UUID productId;

  @Column(nullable = false, length = 16)
  String productKind;

  @Column(nullable = false, length = 100)
  String productName;

  @Column(nullable = false, precision = 8, scale = 2)
  BigDecimal unitPrice;

  @Column(nullable = false)
  int quantity;

  @Column(nullable = false)
  int position;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  Map<String, String> selections;

  /** ORM 重建入口. */
  protected CartItemEntity() {}

  static CartItemEntity create(CartEntity cart, CartItem item) {
    var entity = new CartItemEntity();
    entity.id = item.id();
    entity.cart = cart;
    entity.apply(item);
    return entity;
  }

  void apply(CartItem item) {
    productId = item.productId();
    productKind = item.productKind();
    productName = item.productName();
    unitPrice = item.unitPrice();
    quantity = item.quantity();
    selections = item.selections();
  }

  CartItem domain() {
    return new CartItem(id, productId, productKind, productName, unitPrice, quantity, selections);
  }
}
