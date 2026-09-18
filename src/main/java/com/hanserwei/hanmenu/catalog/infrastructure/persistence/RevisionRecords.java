package com.hanserwei.hanmenu.catalog.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** 对目录修订行加事务级悲观锁，覆盖分类、商品和套餐引用变更的整个事务. */
interface RevisionRecords extends JpaRepository<RevisionEntity, Integer> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<RevisionEntity> findLockedById(Integer id);
}
