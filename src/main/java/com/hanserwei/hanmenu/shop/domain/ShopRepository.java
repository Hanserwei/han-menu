package com.hanserwei.hanmenu.shop.domain;

/** 单店持久化端口，ORM 版本锁阻止并发状态覆盖. */
public interface ShopRepository {
  /** 加载唯一门店. */
  Shop get();

  /** 锁定营业状态直到调用方事务结束，避免下单检查后门店状态被并发改写. */
  Shop lock();

  /** 保存聚合当前状态. */
  void save(Shop shop);
}
