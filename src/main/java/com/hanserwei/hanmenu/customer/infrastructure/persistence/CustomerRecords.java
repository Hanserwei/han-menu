package com.hanserwei.hanmenu.customer.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** 顾客账号派生查询，避免手写 SQL. */
interface CustomerRecords extends JpaRepository<CustomerEntity, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<CustomerEntity> findLockedById(UUID id);

  java.util.List<CustomerEntity> findAllByOrderByIdAsc(
      org.springframework.data.domain.Pageable page);

  java.util.List<CustomerEntity> findByIdGreaterThanOrderByIdAsc(
      UUID cursor, org.springframework.data.domain.Pageable page);

  Optional<CustomerEntity> findByPhone(String phone);
}
