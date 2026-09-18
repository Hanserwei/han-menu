package com.hanserwei.hanmenu.customer.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 地址查询方法始终携带 customerId，阻止跨顾客读取. */
interface AddressRecords extends JpaRepository<AddressEntity, UUID> {
  Optional<AddressEntity> findByCustomerIdAndId(UUID customerId, UUID id);

  List<AddressEntity> findByCustomerIdOrderByDefaultAddressDescCreatedAtDescIdAsc(UUID customerId);

  List<AddressEntity> findByCustomerIdAndDefaultAddressTrue(UUID customerId);
}
