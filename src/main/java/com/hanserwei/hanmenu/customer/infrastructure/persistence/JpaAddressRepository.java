package com.hanserwei.hanmenu.customer.infrastructure.persistence;

import com.hanserwei.hanmenu.customer.domain.AddressRepository;
import com.hanserwei.hanmenu.customer.domain.DeliveryAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 收货地址 JPA 适配器，先按归属读取再执行版本更新. */
@Repository
@Transactional
class JpaAddressRepository implements AddressRepository {
  private final AddressRecords records;
  private final CustomerRecords customers;
  private final java.time.Clock clock;

  JpaAddressRepository(AddressRecords records, CustomerRecords customers, java.time.Clock clock) {
    this.records = records;
    this.customers = customers;
    this.clock = clock;
  }

  @Override
  public void lockOwner(UUID customerId) {
    customers.findLockedById(customerId).orElseThrow();
  }

  @Override
  public void add(DeliveryAddress address) {
    records.saveAndFlush(AddressEntity.create(address));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<DeliveryAddress> findByCustomerAndId(UUID customerId, UUID id) {
    return records.findByCustomerIdAndId(customerId, id).map(AddressEntity::domain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<DeliveryAddress> findByCustomer(UUID customerId) {
    return records.findByCustomerIdOrderByDefaultAddressDescCreatedAtDescIdAsc(customerId).stream()
        .map(AddressEntity::domain)
        .toList();
  }

  @Override
  public void update(DeliveryAddress address) {
    var entity =
        records
            .findByCustomerIdAndId(address.customerId(), address.id())
            .orElseThrow(
                () ->
                    new ObjectOptimisticLockingFailureException(AddressEntity.class, address.id()));
    if (entity.version != address.version()) {
      throw new ObjectOptimisticLockingFailureException(AddressEntity.class, address.id());
    }
    entity.apply(address);
    records.flush();
  }

  @Override
  public void delete(DeliveryAddress address) {
    records.deleteById(address.id());
    records.flush();
  }

  @Override
  public void clearDefault(UUID customerId, UUID exceptId) {
    for (var entity : records.findByCustomerIdAndDefaultAddressTrue(customerId)) {
      if (!entity.id.equals(exceptId)) {
        entity.defaultAddress = false;
        entity.updatedAt = clock.instant();
      }
    }
    // 先 flush 清除旧默认，再写入新默认，避免部分唯一索引在中间状态冲突。
    records.flush();
  }
}
