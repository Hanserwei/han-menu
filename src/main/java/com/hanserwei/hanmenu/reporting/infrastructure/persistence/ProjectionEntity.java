package com.hanserwei.hanmenu.reporting.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/** Projection 的统计模块内部 ORM 投影. */
@Entity(name = "ReportProjection")
@Table(name = "reporting_projection")
public class ProjectionEntity {
  @Id Integer id;
  @Version Long version;
  long generation;
  long revision;
  boolean initialized;
  Instant updatedAt;
  Instant rebuiltAt;

  /** ORM 重建入口. */
  protected ProjectionEntity() {}
}
