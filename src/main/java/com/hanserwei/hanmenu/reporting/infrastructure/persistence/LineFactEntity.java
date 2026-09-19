package com.hanserwei.hanmenu.reporting.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** 成交明细只服务销量聚合，关联全部位于统计模块内部. */
@Entity(name = "ReportLineFact")
@Table(name = "reporting_line")
public class LineFactEntity {
  @Id UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  OrderFactEntity order;

  @Column(name = "product_id")
  UUID productId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "product_id", insertable = false, updatable = false)
  ProductFactEntity product;

  String kind;
  int quantity;

  @Column(precision = 18, scale = 2)
  BigDecimal subtotal;

  /** ORM 重建入口. */
  protected LineFactEntity() {}
}
