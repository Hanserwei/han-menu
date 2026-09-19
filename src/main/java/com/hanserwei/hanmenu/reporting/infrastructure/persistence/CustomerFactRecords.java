package com.hanserwei.hanmenu.reporting.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** CustomerFact 的统计表派生查询. */
interface CustomerFactRecords extends JpaRepository<CustomerFactEntity, java.util.UUID> {
  long countByCreatedDateBefore(java.time.LocalDate before);
}
