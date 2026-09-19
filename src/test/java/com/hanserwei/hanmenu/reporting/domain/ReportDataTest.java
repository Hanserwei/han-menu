package com.hanserwei.hanmenu.reporting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 验证经营日边界、精度、零日期补齐以及收退款与营业额的不同口径. */
class ReportDataTest {
  @Test
  void utcMidnightIsNotTheBusinessDayBoundaryAndMicrosecondsRoundLikePostgres() {
    assertThat(BusinessTime.date(Instant.parse("2026-09-18T15:59:59Z")))
        .isEqualTo(LocalDate.parse("2026-09-18"));
    assertThat(BusinessTime.date(Instant.parse("2026-09-18T16:00:00Z")))
        .isEqualTo(LocalDate.parse("2026-09-19"));
    assertThat(BusinessTime.canonical(Instant.parse("2026-09-18T15:59:59.999999999Z")))
        .isEqualTo(Instant.parse("2026-09-18T16:00:00Z"));
    assertThat(BusinessTime.canonical(Instant.parse("2026-09-19T00:00:00.123456789Z")))
        .isEqualTo(Instant.parse("2026-09-19T00:00:00.123457Z"));
  }

  @Test
  void datesAreBoundedAndEmptyDaysRemainExplicit() {
    var from = LocalDate.parse("2026-09-18");
    assertThatThrownBy(() -> new ReportPeriod(from, from.minusDays(1)))
        .isInstanceOf(ReportingException.class);
    assertThatThrownBy(() -> new ReportPeriod(from, from.plusDays(366)))
        .isInstanceOf(ReportingException.class);
    var days =
        ReportData.days(
            new ReportPeriod(from, from.plusDays(2)),
            new ReportData.Aggregates(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new ReportData.CustomerDay(from.plusDays(1), 2)),
                10));
    assertThat(days).hasSize(3);
    assertThat(days.getFirst().completionRate()).isEqualByComparingTo("0.00");
    assertThat(days.getLast().cumulativeCustomers()).isEqualTo(12);
    assertThat(ReportData.summary(days).totalCustomers()).isEqualTo(12);
  }

  @Test
  void refundsCanMakeDailyCashNegativeWithoutReducingCompletedTurnover() {
    var date = LocalDate.parse("2026-09-19");
    var days =
        ReportData.days(
            new ReportPeriod(date, date),
            new ReportData.Aggregates(
                List.of(new ReportData.OrderDay(date, 3, 1, 1)),
                List.of(new ReportData.CompletionDay(date, 1, new BigDecimal("10.05"))),
                List.of(new ReportData.CashDay(date, 1, new BigDecimal("1.00"))),
                List.of(new ReportData.CashDay(date, 1, new BigDecimal("2.50"))),
                List.of(),
                0));
    var summary = ReportData.summary(days);
    assertThat(summary.turnover()).isEqualByComparingTo("10.05");
    assertThat(summary.netReceipts()).isEqualByComparingTo("-1.50");
    assertThat(summary.completionRate()).isEqualByComparingTo("33.33");
    assertThat(summary.averageOrderValue()).isEqualByComparingTo("10.05");
  }
}
