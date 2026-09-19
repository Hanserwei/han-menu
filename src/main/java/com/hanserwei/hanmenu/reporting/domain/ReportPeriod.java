package com.hanserwei.hanmenu.reporting.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** 首尾日期均包含的有界经营区间，最多三百六十六天. */
public record ReportPeriod(LocalDate from, LocalDate to) {
  /** 验证协议日期及区间上限，防止无界聚合和导出. */
  public ReportPeriod {
    if (from == null
        || to == null
        || from.isAfter(to)
        || from.getYear() < 1970
        || to.getYear() > 9999
        || ChronoUnit.DAYS.between(from, to) >= 366) {
      throw new ReportingException(
          ReportingException.Reason.INVALID_INPUT, "报表区间须为 1970 至 9999 年内连续 1 至 366 天");
    }
  }

  /** 返回包含首尾日的天数. */
  public int days() {
    return Math.toIntExact(ChronoUnit.DAYS.between(from, to) + 1);
  }
}
