package com.hanserwei.hanmenu.reporting.application;

import com.hanserwei.hanmenu.reporting.domain.BusinessTime;
import com.hanserwei.hanmenu.reporting.domain.ProjectionState;
import com.hanserwei.hanmenu.reporting.domain.ReportWorkbook;
import com.hanserwei.hanmenu.reporting.domain.ReportingRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 集中把统计值模型映射为 HTTP DTO，报表不暴露投影实体或顾客个人资料. */
public final class ReportViews {
  private ReportViews() {}

  /** 返回控制版本与记录数，供重建操作校验及验收. */
  public static ProjectionStatus projection(
      ProjectionState state, ReportingRepository.Counts counts) {
    return new ProjectionStatus(
        state.version(),
        state.generation(),
        state.revision(),
        state.initialized(),
        state.updatedAt(),
        state.rebuiltAt(),
        counts.orders(),
        counts.customers(),
        counts.receipts(),
        counts.refunds());
  }

  /** 映射经营汇总和补齐后的日账. */
  public static Operations operations(ReportWorkbook report) {
    var sum = report.summary();
    return new Operations(
        report.period().from(),
        report.period().to(),
        BusinessTime.ZONE.getId(),
        "CNY",
        metadata(report.metadata()),
        new Summary(
            sum.submitted(),
            sum.completedCohort(),
            sum.cancelledCohort(),
            sum.completed(),
            sum.turnover(),
            sum.receipts(),
            sum.refunds(),
            sum.netReceipts(),
            sum.newCustomers(),
            sum.totalCustomers(),
            sum.completionRate(),
            sum.averageOrderValue()),
        report.days().stream()
            .map(
                day ->
                    new Day(
                        day.date(),
                        day.submitted(),
                        day.completedCohort(),
                        day.cancelledCohort(),
                        day.completed(),
                        day.turnover(),
                        day.receipts(),
                        day.refunds(),
                        day.netReceipts(),
                        day.newCustomers(),
                        day.cumulativeCustomers(),
                        day.completionRate()))
            .toList());
  }

  /** 映射当前区间的销量排行. */
  public static Sales sales(ReportWorkbook report) {
    return new Sales(
        report.period().from(),
        report.period().to(),
        "CNY",
        metadata(report.metadata()),
        report.sales().stream()
            .map(
                value ->
                    new Sale(
                        value.productId(),
                        value.kind(),
                        value.name(),
                        value.quantity(),
                        value.amount()))
            .toList());
  }

  /** 对账报告仅报告差异，不修正或补造资金流水. */
  public static Reconciliation reconciliation(ReportWorkbook report) {
    var value = report.reconciliation();
    var sum = report.summary();
    return new Reconciliation(
        report.period().from(),
        report.period().to(),
        "CNY",
        metadata(report.metadata()),
        sum.receipts(),
        sum.refunds(),
        sum.netReceipts(),
        value.missingOrders(),
        value.paymentMismatches(),
        value.missingPayments(),
        value.refundMismatches(),
        value.pendingRefunds(),
        value.ordersMissingReceipts(),
        value.ordersMissingRefunds());
  }

  /** 复制不可变投影元数据. */
  public static Metadata metadata(ReportWorkbook.Metadata value) {
    return new Metadata(
        value.version(),
        value.generation(),
        value.revision(),
        value.updatedAt(),
        value.rebuiltAt());
  }

  /** 报表时点由持久化投影版本描述，异步滞后不能被隐藏. */
  public record Metadata(
      long version, long generation, long revision, Instant updatedAt, Instant rebuiltAt) {}

  /** 管理员投影维护状态. */
  public record ProjectionStatus(
      long version,
      long generation,
      long revision,
      boolean initialized,
      Instant updatedAt,
      Instant rebuiltAt,
      long orders,
      long customers,
      long receipts,
      long refunds) {}

  /** 经营汇总不将营业额与现金净流入混为一谈. */
  public record Summary(
      long submittedOrders,
      long completedCohort,
      long cancelledCohort,
      long completedOrders,
      BigDecimal turnover,
      BigDecimal receivedAmount,
      BigDecimal refundedAmount,
      BigDecimal netReceivedAmount,
      long newCustomers,
      long totalCustomers,
      BigDecimal completionRatePercent,
      BigDecimal averageOrderValue) {}

  /** 日账按固定经营日期补齐零值. */
  public record Day(
      LocalDate date,
      long submittedOrders,
      long completedCohort,
      long cancelledCohort,
      long completedOrders,
      BigDecimal turnover,
      BigDecimal receivedAmount,
      BigDecimal refundedAmount,
      BigDecimal netReceivedAmount,
      long newCustomers,
      long cumulativeCustomers,
      BigDecimal completionRatePercent) {}

  /** 经营、订单和顾客增长的统一响应. */
  public record Operations(
      LocalDate from,
      LocalDate to,
      String zone,
      String currency,
      Metadata projection,
      Summary summary,
      List<Day> days) {
    /** 固定日期序列. */
    public Operations {
      days = List.copyOf(days);
    }
  }

  /** 销量排行仅按已完成订单的完成日统计，不拆分改名后的同一商品. */
  public record Sale(UUID productId, String kind, String name, long quantity, BigDecimal amount) {}

  /** 有界销量查询响应. */
  public record Sales(
      LocalDate from, LocalDate to, String currency, Metadata projection, List<Sale> items) {
    /** 固定排行列表. */
    public Sales {
      items = List.copyOf(items);
    }
  }

  /** 对账差异在投影补齐后应归零，待退款数是全店当前待处理数. */
  public record Reconciliation(
      LocalDate from,
      LocalDate to,
      String currency,
      Metadata projection,
      BigDecimal receivedAmount,
      BigDecimal refundedAmount,
      BigDecimal netReceivedAmount,
      long missingOrders,
      long paymentMismatches,
      long missingPayments,
      long refundMismatches,
      long pendingRefunds,
      long ordersMissingReceipts,
      long ordersMissingRefunds) {}

  /** 员工工作台不公开资金金额，只返回业务待办与当前目录数量. */
  public record Workspace(
      LocalDate businessDate,
      String zone,
      String shopStatus,
      Metadata projection,
      long awaitingAcceptance,
      long accepted,
      long delivering,
      long cancelling,
      long refunding,
      long createdToday,
      long completedToday,
      long dishesOnSale,
      long dishesOffSale,
      long mealsOnSale,
      long mealsOffSale) {}
}
