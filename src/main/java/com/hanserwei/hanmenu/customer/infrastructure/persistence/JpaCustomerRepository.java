package com.hanserwei.hanmenu.customer.infrastructure.persistence;

import com.hanserwei.hanmenu.customer.domain.CustomerAccount;
import com.hanserwei.hanmenu.customer.domain.CustomerRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 顾客聚合 JPA 仓储适配器. */
@Repository
@Transactional
class JpaCustomerRepository implements CustomerRepository {
  private final CustomerRecords records;

  JpaCustomerRepository(CustomerRecords records) {
    this.records = records;
  }

  @Override
  public void add(CustomerAccount customer) {
    records.saveAndFlush(CustomerEntity.create(customer));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CustomerAccount> findById(UUID id) {
    return records.findById(id).map(CustomerEntity::domain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CustomerAccount> findByPhone(String phone) {
    return records.findByPhone(phone).map(CustomerEntity::domain);
  }

  @Override
  public void update(CustomerAccount customer) {
    var entity =
        records
            .findById(customer.id())
            .orElseThrow(
                () ->
                    new ObjectOptimisticLockingFailureException(
                        CustomerEntity.class, customer.id()));
    if (entity.version != customer.version()) {
      throw new ObjectOptimisticLockingFailureException(CustomerEntity.class, customer.id());
    }
    entity.apply(customer);
    records.flush();
  }
}
