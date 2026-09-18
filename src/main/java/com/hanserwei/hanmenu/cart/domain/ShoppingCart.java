package com.hanserwei.hanmenu.cart.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 顾客购物车聚合，封装同商品同规格合并和条目数量变更. */
public final class ShoppingCart {
  private final UUID customerId;
  private final List<CartItem> items;
  private final long version;
  private final Instant updatedAt;

  /** 从持久化快照重建购物车. */
  public ShoppingCart(UUID customerId, List<CartItem> items, long version, Instant updatedAt) {
    this.customerId = Objects.requireNonNull(customerId);
    this.items = new ArrayList<>(items);
    this.version = version;
    this.updatedAt = Objects.requireNonNull(updatedAt);
    if (items.size() > 50) {
      throw new CartException(CartException.Reason.CONFLICT, "购物车最多 50 个不同规格条目");
    }
    if (version < 0) {
      throw new IllegalArgumentException("购物车版本不可为负");
    }
  }

  /** 返回以新版本表示的空购物车. */
  public static ShoppingCart empty(UUID customerId, Instant now) {
    return new ShoppingCart(customerId, List.of(), 0, now);
  }

  /** 增加商品；同商品同规格合并，避免顾客重复添加产生无意义重复行. */
  public ShoppingCart add(CartItem item, int amount, Instant now) {
    var next = new ArrayList<>(items);
    int index = -1;
    for (int i = 0; i < next.size(); i++) {
      if (next.get(i).sameSelection(item.productId(), item.selections())) {
        index = i;
        break;
      }
    }
    if (index >= 0) {
      var old = next.get(index);
      int quantity = old.addQuantity(amount).quantity();
      next.set(
          index,
          new CartItem(
              old.id(),
              item.productId(),
              item.productKind(),
              item.productName(),
              item.unitPrice(),
              quantity,
              item.selections()));
    } else {
      next.add(item.changeQuantity(amount));
    }
    return new ShoppingCart(customerId, next, version, now);
  }

  /** 修改已有条目数量. */
  public ShoppingCart changeQuantity(UUID itemId, int quantity, Instant now) {
    return mapItem(itemId, item -> item.changeQuantity(quantity), now);
  }

  /** 删除条目，删除不存在条目返回资源错误而不是静默成功. */
  public ShoppingCart remove(UUID itemId, Instant now) {
    if (items.stream().noneMatch(item -> item.id().equals(itemId))) {
      throw new CartException(CartException.Reason.NOT_FOUND, "购物车条目不存在");
    }
    return new ShoppingCart(
        customerId,
        items.stream().filter(item -> !item.id().equals(itemId)).toList(),
        version,
        now);
  }

  /** 清空当前购物车. */
  public ShoppingCart clear(Instant now) {
    return new ShoppingCart(customerId, List.of(), version, now);
  }

  /** 校验写操作使用最新购物车版本. */
  public void requireVersion(long expected) {
    if (version != expected) {
      throw new CartException(CartException.Reason.VERSION_CONFLICT, "购物车已被修改");
    }
  }

  private ShoppingCart mapItem(
      UUID itemId, java.util.function.UnaryOperator<CartItem> mapper, Instant now) {
    boolean found = false;
    var next = new ArrayList<CartItem>();
    for (var item : items) {
      if (item.id().equals(itemId)) {
        next.add(mapper.apply(item));
        found = true;
      } else {
        next.add(item);
      }
    }
    if (!found) {
      throw new CartException(CartException.Reason.NOT_FOUND, "购物车条目不存在");
    }
    return new ShoppingCart(customerId, next, version, now);
  }

  /** 返回顾客标识. */
  public UUID customerId() {
    return customerId;
  }

  /** 返回不可修改的条目快照. */
  public List<CartItem> items() {
    return List.copyOf(items);
  }

  /** 返回持久化版本. */
  public long version() {
    return version;
  }

  /** 返回最后更新时间. */
  public Instant updatedAt() {
    return updatedAt;
  }
}
