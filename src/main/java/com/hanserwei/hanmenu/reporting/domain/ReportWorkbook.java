package com.hanserwei.hanmenu.reporting.domain;

import java.time.Instant;
import java.util.List;

/** 报表和导出共用的不可变一致性快照，文件生成不再读取数据库. */
public record ReportWorkbook(
    ReportPeriod period,
    Metadata metadata,
    ReportData.Summary summary,
    List<ReportData.Day> days,
    List<ReportData.Sale> sales,
    ReportData.Reconciliation reconciliation) {
  /** 固定全部明细集合. */
  public ReportWorkbook {
    days = List.copyOf(days);
    sales = List.copyOf(sales);
  }

  /** 公开统计版本不暴露可变的控制聚合. */
  public record Metadata(
      long version,
      long generation,
      long revision,
      boolean initialized,
      Instant updatedAt,
      Instant rebuiltAt) {}
}
