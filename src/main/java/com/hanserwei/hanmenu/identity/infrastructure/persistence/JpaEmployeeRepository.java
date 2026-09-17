package com.hanserwei.hanmenu.identity.infrastructure.persistence;

import com.hanserwei.hanmenu.identity.domain.EmployeeAccount;
import com.hanserwei.hanmenu.identity.domain.EmployeePage;
import com.hanserwei.hanmenu.identity.domain.EmployeeRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 将聚合仓储端口适配到 Spring Data JPA，显式映射模型而不维护业务 SQL. */
@Repository
@Transactional
class JpaEmployeeRepository implements EmployeeRepository {
  private final EmployeeRecords records;

  JpaEmployeeRepository(EmployeeRecords records) {
    this.records = records;
  }

  @Override
  public boolean hasAdministrator() {
    return records.existsByRole(EmployeeAccount.Role.ADMIN);
  }

  @Override
  public void add(EmployeeAccount account) {
    records.saveAndFlush(EmployeeEntity.create(account));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<EmployeeAccount> findById(UUID id) {
    return records.findById(id).map(EmployeeEntity::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<EmployeeAccount> findByUsername(String username) {
    return records.findByUsername(username).map(EmployeeEntity::toDomain);
  }

  @Override
  public void update(EmployeeAccount account) {
    var entity =
        records
            .findById(account.id())
            .orElseThrow(
                () ->
                    new ObjectOptimisticLockingFailureException(
                        EmployeeEntity.class, account.id()));
    if (entity.version() != account.version()) {
      throw new ObjectOptimisticLockingFailureException(EmployeeEntity.class, account.id());
    }
    entity.apply(account);
    // 先校验已加载版本，再由 Hibernate 的 @Version 检测 flush 时发生的并发更新。
    records.flush();
  }

  @Override
  @Transactional(readOnly = true)
  public EmployeePage search(String displayName, int page, int size) {
    var ordering = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"));
    var result =
        records.findByDisplayNameContainingIgnoreCase(
            displayName, PageRequest.of(page, size, ordering));
    return new EmployeePage(
        result.getContent().stream().map(EmployeeEntity::toDomain).toList(),
        result.getTotalElements());
  }
}
