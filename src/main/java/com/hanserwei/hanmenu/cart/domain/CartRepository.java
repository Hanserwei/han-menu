package com.hanserwei.hanmenu.cart.domain;

import java.util.Optional;
import java.util.UUID;

/** 购物车聚合仓储端口. */
public interface CartRepository {
  /** 读取顾客购物车，不存在时返回空. */
  Optional<ShoppingCart> findByCustomer(UUID customerId);

  /** 创建新购物车. */
  void add(ShoppingCart cart);

  /** 按版本更新购物车. */
  void update(ShoppingCart cart);
}
