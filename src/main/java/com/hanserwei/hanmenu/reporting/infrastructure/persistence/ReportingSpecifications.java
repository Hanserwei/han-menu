package com.hanserwei.hanmenu.reporting.infrastructure.persistence;

import com.hanserwei.hanmenu.reporting.domain.ReportPeriod;
import java.time.LocalDate;
import org.springframework.data.jpa.domain.Specification;

/** 统计组合条件集中复用 Specification，分组与金额聚合继续由 JPA Criteria 完成. */
final class ReportingSpecifications {
  private ReportingSpecifications() {}

  static <T> Specification<T> dates(String attribute, ReportPeriod period) {
    return (root, query, builder) ->
        builder.between(root.<LocalDate>get(attribute), period.from(), period.to());
  }

  static Specification<OrderFactEntity> completed(ReportPeriod period) {
    return ReportingSpecifications.<OrderFactEntity>dates("completedDate", period)
        .and((root, query, builder) -> builder.equal(root.get("status"), "COMPLETED"));
  }

  static Specification<LineFactEntity> completedSales(ReportPeriod period) {
    return (root, query, builder) -> {
      var order = root.join("order");
      return builder.and(
          builder.equal(order.get("status"), "COMPLETED"),
          builder.between(order.get("completedDate"), period.from(), period.to()));
    };
  }
}
