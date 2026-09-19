package com.hanserwei.hanmenu.reporting.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** OrderFact 的统计表派生查询. */
interface OrderFactRecords extends JpaRepository<OrderFactEntity, java.util.UUID> {
  long countByCreatedDate(java.time.LocalDate date);

  long countByStatusAndCompletedDate(String status, java.time.LocalDate date);

  long countByRefundStatus(String status);
}
