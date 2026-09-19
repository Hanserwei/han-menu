package com.hanserwei.hanmenu.reporting.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** CustomerFact 的统计模块内部 ORM 投影. */
@Entity(name = "ReportCustomerFact")
@Table(name = "reporting_customer")
public class CustomerFactEntity {
  @Id UUID id;
  @Version Long version;
  Instant createdAt;
  LocalDate createdDate;

  /** ORM 重建入口. */
  protected CustomerFactEntity() {}
}
