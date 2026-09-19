package com.hanserwei.hanmenu.reporting.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** ProductFact 的统计模块内部 ORM 投影. */
@Entity(name = "ReportProductFact")
@Table(name = "reporting_product")
public class ProductFactEntity {
  @Id UUID id;
  @Version Long version;
  String name;
  String kind;
  Instant observedAt;
  UUID sourceOrderId;

  /** ORM 重建入口. */
  protected ProductFactEntity() {}
}
