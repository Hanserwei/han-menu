package com.hanserwei.hanmenu.reporting.infrastructure.persistence;

import com.hanserwei.hanmenu.reporting.domain.BusinessTime;
import com.hanserwei.hanmenu.reporting.domain.ReportingFacts;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 订单统计投影只保存业务状态和成交明细，不复制收货信息. */
@Entity(name = "ReportOrderFact")
@Table(name = "reporting_order")
public class OrderFactEntity {
  @Id UUID id;
  @Version Long version;
  UUID customerId;
  long sourceVersion;
  String status;

  @Column(precision = 16, scale = 2)
  BigDecimal total;

  Instant createdAt;
  LocalDate createdDate;
  Instant paidAt;
  Instant completedAt;
  LocalDate completedDate;
  Instant cancelledAt;
  UUID paymentId;
  String refundStatus;
  UUID refundId;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  List<LineFactEntity> lines = new ArrayList<>();

  /** ORM 重建入口. */
  protected OrderFactEntity() {}

  static OrderFactEntity from(ReportingFacts.Order value) {
    var entity = new OrderFactEntity();
    entity.id = value.id();
    entity.customerId = value.customerId();
    entity.createdAt = value.createdAt();
    entity.createdDate = BusinessTime.date(value.createdAt());
    entity.total = value.total();
    entity.apply(value);
    for (var line : value.lines()) {
      var item = new LineFactEntity();
      item.id = line.id();
      item.order = entity;
      item.productId = line.productId();
      item.kind = line.kind();
      item.quantity = line.quantity();
      item.subtotal = line.subtotal();
      entity.lines.add(item);
    }
    return entity;
  }

  void apply(ReportingFacts.Order value) {
    paidAt = value.paidAt();
    sourceVersion = value.sourceVersion();
    status = value.status();
    completedAt = value.completedAt();
    completedDate = BusinessTime.date(value.completedAt());
    cancelledAt = value.cancelledAt();
    paymentId = value.paymentId();
    refundStatus = value.refundStatus();
    refundId = value.refundId();
  }
}
