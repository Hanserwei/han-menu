package com.hanserwei.hanmenu.catalog.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 分类查询由 Spring Data 派生，无手写 SQL. */
interface CategoryRecords extends JpaRepository<CategoryEntity, UUID> {
  List<CategoryEntity> findAllByOrderBySortOrderAscIdAsc();

  List<CategoryEntity> findByEnabledTrueOrderBySortOrderAscIdAsc();
}
