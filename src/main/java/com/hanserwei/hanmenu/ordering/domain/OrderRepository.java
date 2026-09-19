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

  /** 按订单行锁串行化付款、取消、事件和履约，不读取其他模块业务表. */
  Order lock(UUID id);

  /** 后台读取订单，权限由应用用例验证. */
  Optional<Order> find(UUID id);

  /** 查询有界过期待付款订单标识，逐项进入独立短事务. */
  java.util.List<UUID> expired(java.time.Instant before, int limit);

  /** 后台按可选状态分页，组合查询由 ORM Specification 完成. */
  OrderPage management(Order.Status status, int page, int size);

  /** 按标识键集分页导出统计事实，避免集合抓取分页或无界读取. */
  java.util.List<Order> factsAfter(UUID cursor, int limit);
}
