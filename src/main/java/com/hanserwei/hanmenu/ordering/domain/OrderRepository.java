package com.hanserwei.hanmenu.ordering.domain;

import java.util.Optional;
import java.util.UUID;

/** 订单仓储只暴露聚合与有界查询，不导出持久化实体. */
public interface OrderRepository {
  /** 在当前事务内保存新订单及不可变条目. */
  void add(Order order);

  /** 按归属读取订单，外部顾客的标识与不存在统一处理. */
  Optional<Order> findOwned(UUID customerId, UUID id);

  /** 按顾客及提交幂等键读取原业务结果. */
  Optional<Order> findSubmitted(UUID customerId, String key);

  /** 仅保存生命周期状态并校验 ORM 版本，不改写历史快照. */
  void update(Order order);

  /** 按创建时间和标识稳定排序读取本人订单历史. */
  OrderPage history(UUID customerId, int page, int size);
}
