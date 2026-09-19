package com.hanserwei.hanmenu.reporting.application;

import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import com.hanserwei.hanmenu.reporting.domain.ReportExporter;
import com.hanserwei.hanmenu.reporting.domain.ReportPeriod;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/** 在查询事务外完成 DTO 或文件生成，避免 XLSX 压缩占用数据库事务. */
@Service
public class ReportingService {
  private final ReportQueries queries;
  private final ReportExporter exporter;

  /** 注入快照查询与文件格式端口. */
  public ReportingService(ReportQueries queries, ReportExporter exporter) {
    this.queries = queries;
    this.exporter = exporter;
  }

  /** 返回经营、订单和顾客增长日账. */
  public ReportViews.Operations operations(StaffIdentity actor, LocalDate from, LocalDate to) {
    return ReportViews.operations(queries.load(actor, new ReportPeriod(from, to), 10));
  }

  /** 返回按销量稳定排序的商品排行. */
  public ReportViews.Sales sales(StaffIdentity actor, LocalDate from, LocalDate to, int limit) {
    return ReportViews.sales(queries.load(actor, new ReportPeriod(from, to), limit));
  }

  /** 返回同一资金区间的收退款及差异. */
  public ReportViews.Reconciliation reconcile(StaffIdentity actor, LocalDate from, LocalDate to) {
    return ReportViews.reconciliation(queries.load(actor, new ReportPeriod(from, to), 10));
  }

  /** 导出和界面使用同一统计快照和金额规则，不重新查询其他业务表. */
  public byte[] export(StaffIdentity actor, LocalDate from, LocalDate to) {
    return exporter.export(queries.load(actor, new ReportPeriod(from, to), 100));
  }
}
