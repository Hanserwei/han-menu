package com.hanserwei.hanmenu.reporting.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Projection 的统计表派生查询. */
interface ProjectionRecords extends JpaRepository<ProjectionEntity, Integer> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  java.util.Optional<ProjectionEntity> findLockedById(Integer id);
}
