package com.hanserwei.hanmenu.reporting.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 报表值模型把经营收入、真实收退款和创建订单群组口径明确分开. */
public final class ReportData {
  private ReportData() {}

  /** 当日创建订单群组的当前完成及取消数量. */
  public record OrderDay(
      LocalDate date, long submitted, long completedCohort, long cancelledCohort) {}

  /** 按完成日计算的交付订单数量和营业额. */
  public record CompletionDay(LocalDate date, long completed, BigDecimal turnover) {}

  /** 按确认事实发生日计算资金金额，不以订单状态代替资金记录. */
  public record CashDay(LocalDate date, long count, BigDecimal amount) {}

  /** 注册日新增顾客数. */
  public record CustomerDay(LocalDate date, long added) {}

  /** 原始聚合结果不包含个人资料，所有集合有界. */
  public record Aggregates(
      List<OrderDay> orders,
      List<CompletionDay> completions,
      List<CashDay> receipts,
      List<CashDay> refunds,
      List<CustomerDay> customers,
      long customersBefore) {}

  /** 补齐零值日期的统一日账，金额始终精确到分. */
  public record Day(
      LocalDate date,
      long submitted,
      long completedCohort,
      long cancelledCohort,
      long completed,
      BigDecimal turnover,
      BigDecimal receipts,
      BigDecimal refunds,
      long newCustomers,
      long cumulativeCustomers) {
    /** 同一创建群组的订单完成率，零分母返回零. */
    public BigDecimal completionRate() {
      return percent(completedCohort, submitted);
    }

    /** 净收款按资金日账相减，允许跨日退款造成负值. */
    public BigDecimal netReceipts() {
      return receipts.subtract(refunds);
    }
  }

  /** 商品数量和成交金额按商品 UUID 合并，名称来自最近订单快照. */
  public record Sale(UUID productId, String kind, String name, long quantity, BigDecimal amount) {}

  /** 资金对账检查和待退款数量，差异只报告不篡改业务事实. */
  public record Reconciliation(
      long missingOrders,
      long paymentMismatches,
      long missingPayments,
      long refundMismatches,
      long pendingRefunds,
      long ordersMissingReceipts,
      long ordersMissingRefunds) {}

  /** 总览金额由同一批日账相加，导出与 HTTP 使用相同数据. */
  public record Summary(
      long submitted,
      long completedCohort,
      long cancelledCohort,
      long completed,
      BigDecimal turnover,
      BigDecimal receipts,
      BigDecimal refunds,
      long newCustomers,
      long totalCustomers) {
    /** 创建群组口径的完成率. */
    public BigDecimal completionRate() {
      return percent(completedCohort, submitted);
    }

    /** 收款减已确认退款，不把待退款计为现金流出. */
    public BigDecimal netReceipts() {
      return receipts.subtract(refunds);
    }

    /** 已完成订单平均成交金额，零完成时返回零. */
    public BigDecimal averageOrderValue() {
      return completed == 0
          ? zero()
          : turnover.divide(BigDecimal.valueOf(completed), 2, RoundingMode.HALF_UP);
    }
  }

  /** 用有界日期区间补齐缺失日期，并按注册日累计顾客总数. */
  public static List<Day> days(ReportPeriod period, Aggregates data) {
    var orders = index(data.orders(), OrderDay::date);
    var completions = index(data.completions(), CompletionDay::date);
    var receipts = index(data.receipts(), CashDay::date);
    var refunds = index(data.refunds(), CashDay::date);
    var customers = index(data.customers(), CustomerDay::date);
    var result = new java.util.ArrayList<Day>();
    long cumulative = data.customersBefore();
    for (int offset = 0; offset < period.days(); offset++) {
      var date = period.from().plusDays(offset);
      var order = orders.getOrDefault(date, new OrderDay(date, 0, 0, 0));
      var completed = completions.getOrDefault(date, new CompletionDay(date, 0, zero()));
      var incoming = receipts.getOrDefault(date, new CashDay(date, 0, zero()));
      var outgoing = refunds.getOrDefault(date, new CashDay(date, 0, zero()));
      long added = customers.getOrDefault(date, new CustomerDay(date, 0)).added();
      cumulative = Math.addExact(cumulative, added);
      result.add(
          new Day(
              date,
              order.submitted(),
              order.completedCohort(),
              order.cancelledCohort(),
              completed.completed(),
              completed.turnover(),
              incoming.amount(),
              outgoing.amount(),
              added,
              cumulative));
    }
    return List.copyOf(result);
  }

  /** 从完全相同的日账计算汇总，避免两个端点使用不同金额口径. */
  public static Summary summary(List<Day> days) {
    return new Summary(
        days.stream().mapToLong(Day::submitted).sum(),
        days.stream().mapToLong(Day::completedCohort).sum(),
        days.stream().mapToLong(Day::cancelledCohort).sum(),
        days.stream().mapToLong(Day::completed).sum(),
        days.stream().map(Day::turnover).reduce(zero(), BigDecimal::add),
        days.stream().map(Day::receipts).reduce(zero(), BigDecimal::add),
        days.stream().map(Day::refunds).reduce(zero(), BigDecimal::add),
        days.stream().mapToLong(Day::newCustomers).sum(),
        days.isEmpty() ? 0 : days.getLast().cumulativeCustomers());
  }

  private static <T> Map<LocalDate, T> index(List<T> data, Function<T, LocalDate> key) {
    return data.stream().collect(Collectors.toMap(key, Function.identity()));
  }

  private static BigDecimal percent(long numerator, long denominator) {
    return denominator == 0
        ? zero()
        : BigDecimal.valueOf(numerator)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
  }

  private static BigDecimal zero() {
    return new BigDecimal("0.00");
  }
}
