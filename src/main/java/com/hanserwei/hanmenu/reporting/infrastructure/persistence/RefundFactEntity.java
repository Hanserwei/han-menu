package com.hanserwei.hanmenu.reporting.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** RefundFact 的统计模块内部 ORM 投影. */
@Entity(name = "ReportRefundFact")
@Table(name = "reporting_refund")
public class RefundFactEntity {
  @Id UUID id;
  @Version Long version;
  UUID paymentId;
  UUID orderId;

  @Column(precision = 16, scale = 2)
  BigDecimal amount;

  Instant confirmedAt;
  LocalDate businessDate;

  /** ORM 重建入口. */
  protected RefundFactEntity() {}
}
