package com.hanserwei.hanmenu.customer.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 地址实体仓储端口，所有查询必须带顾客归属. */
public interface AddressRepository {
  /** 锁定所属顾客地址簿，默认切换和并发创建在同一事务串行执行. */
  void lockOwner(UUID customerId);

  /** 保存新地址. */
  void add(DeliveryAddress address);

  /** 按顾客和地址读取，防止水平越权. */
  Optional<DeliveryAddress> findByCustomerAndId(UUID customerId, UUID id);

  /** 查询顾客地址列表. */
  List<DeliveryAddress> findByCustomer(UUID customerId);

  /** 按版本更新地址. */
  void update(DeliveryAddress address);

  /** 删除顾客自己的地址. */
  void delete(DeliveryAddress address);

  /** 在同一事务内清除顾客已有默认地址. */
  void clearDefault(UUID customerId, UUID exceptId);
}
