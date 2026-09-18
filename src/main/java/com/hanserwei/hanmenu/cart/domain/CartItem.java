package com.hanserwei.hanmenu.cart.domain;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 购物车条目，保存展示快照但不作为订单最终价格来源. */
public record CartItem(
    UUID id,
    UUID productId,
    String productKind,
    String productName,
    BigDecimal unitPrice,
    int quantity,
    Map<String, String> selections) {
  /** 验证数量、价格和不可变规格选择. */
  public CartItem {
    Objects.requireNonNull(id);
    Objects.requireNonNull(productId);
    Objects.requireNonNull(productKind);
    Objects.requireNonNull(productName);
    Objects.requireNonNull(unitPrice);
    if (productName.isBlank()
        || quantity < 1
        || quantity > 99
        || unitPrice.signum() <= 0
        || selections == null) {
      throw new CartException(CartException.Reason.INVALID_INPUT, "购物车条目不合法");
    }
    selections = Map.copyOf(selections);
  }

  /** 判断同一商品和规格，规格顺序不影响合并. */
  public boolean sameSelection(UUID otherProductId, Map<String, String> otherSelections) {
    return productId.equals(otherProductId) && selections.equals(otherSelections);
  }

  /** 返回增加数量后的新条目，避免通过 setter 绕过上限. */
  public CartItem addQuantity(int amount) {
    if (amount < 1 || quantity + amount > 99) {
      throw new CartException(CartException.Reason.INVALID_INPUT, "购物车数量必须在 1 至 99 之间");
    }
    return new CartItem(
        id, productId, productKind, productName, unitPrice, quantity + amount, selections);
  }

  /** 返回指定数量的新条目. */
  public CartItem changeQuantity(int replacement) {
    return new CartItem(
        id, productId, productKind, productName, unitPrice, replacement, selections);
  }
}
