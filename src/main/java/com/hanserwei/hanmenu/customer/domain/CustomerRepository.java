package com.hanserwei.hanmenu.customer.domain;

import java.util.Optional;
import java.util.UUID;

/** 顾客聚合仓储端口. */
public interface CustomerRepository {
  /** 持久化新顾客. */
  void add(CustomerAccount customer);

  /** 按标识读取顾客. */
  Optional<CustomerAccount> findById(UUID id);

  /** 按手机号读取顾客. */
  Optional<CustomerAccount> findByPhone(String phone);

  /** 按版本更新顾客. */
  void update(CustomerAccount customer);

  /** 按标识有界导出统计快照. */
  java.util.List<CustomerAccount> factsAfter(UUID cursor, int limit);
}
