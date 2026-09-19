package com.hanserwei.hanmenu.reporting.application;

import com.hanserwei.hanmenu.ordering.api.OrderFacts;
import com.hanserwei.hanmenu.reporting.domain.ReportingFacts;

/** 将跨模块公开快照集中转换为统计自己的领域值模型. */
final class FactMapper {
  private FactMapper() {}

  static ReportingFacts.Order order(OrderFacts.Snapshot source) {
    return new ReportingFacts.Order(
        source.id(),
        source.customerId(),
        source.version(),
        source.status(),
        source.total(),
        source.createdAt(),
        source.paidAt(),
        source.completedAt(),
        source.cancelledAt(),
        source.paymentId(),
        source.refundStatus(),
        source.refundId(),
        source.lines().stream()
            .map(
                line ->
                    new ReportingFacts.Line(
                        line.id(),
                        line.productId(),
                        line.name(),
                        line.kind(),
                        line.quantity(),
                        line.unitPrice()))
            .toList());
  }
}
