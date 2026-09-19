package com.hanserwei.hanmenu.reporting.web.admin;

import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import com.hanserwei.hanmenu.reporting.application.ProjectionMaintenance;
import com.hanserwei.hanmenu.reporting.application.ReportQueries;
import com.hanserwei.hanmenu.reporting.application.ReportViews;
import com.hanserwei.hanmenu.reporting.application.ReportingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理员报表资源固定统计口径，普通员工只读取工作台待办. */
@RestController
@Tag(name = "经营报表与工作台")
class ReportController {
  private final ReportingService reports;
  private final ReportQueries queries;
  private final ProjectionMaintenance maintenance;

  ReportController(
      ReportingService reports, ReportQueries queries, ProjectionMaintenance maintenance) {
    this.reports = reports;
    this.queries = queries;
    this.maintenance = maintenance;
  }

  /** 工作台不包含资金或顾客个人资料. */
  @GetMapping("/api/v1/workspace")
  @Operation(summary = "员工工作台待办与当前门店目录摘要")
  ReportViews.Workspace workspace(@AuthenticationPrincipal StaffIdentity actor) {
    return queries.workspace(actor);
  }

  /** 返回经营、订单和顾客增长日账. */
  @GetMapping("/api/v1/reports/operations")
  @Operation(summary = "查询经营汇总和每日统计")
  ReportViews.Operations operations(
      @AuthenticationPrincipal StaffIdentity actor,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return reports.operations(actor, from, to);
  }

  /** 商品按 UUID 汇总，历史改名不会拆分排行. */
  @GetMapping("/api/v1/reports/sales")
  @Operation(summary = "查询已完成订单商品销量排行")
  ReportViews.Sales sales(
      @AuthenticationPrincipal StaffIdentity actor,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
    return reports.sales(actor, from, to, limit);
  }

  /** 收款与退款独立计入各自经营日期，差异只报告不修改源业务. */
  @GetMapping("/api/v1/reports/reconciliation")
  @Operation(summary = "查询收退款金额与投影对账差异")
  ReportViews.Reconciliation reconciliation(
      @AuthenticationPrincipal StaffIdentity actor,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return reports.reconcile(actor, from, to);
  }

  /** 导出标准 XLSX，金额与同区间 HTTP 报表保持一致. */
  @GetMapping("/api/v1/reports/export")
  @Operation(summary = "导出经营日账、销量和资金对账 XLSX")
  ResponseEntity<byte[]> export(
      @AuthenticationPrincipal StaffIdentity actor,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    var bytes = reports.export(actor, from, to);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .header(
            "Content-Disposition",
            ContentDisposition.attachment()
                .filename("han-menu-" + from + "-" + to + ".xlsx")
                .build()
                .toString())
        .contentLength(bytes.length)
        .body(bytes);
  }

  /** 即使初始化失败也返回可恢复状态. */
  @GetMapping("/api/v1/reports/projection")
  @Operation(summary = "查询统计投影代际、版本与记录数")
  ReportViews.ProjectionStatus projection(@AuthenticationPrincipal StaffIdentity actor) {
    return queries.projection(actor);
  }

  /** 管理员按版本原子重建，失败保持旧投影及旧代际. */
  @PostMapping("/api/v1/reports/projection/rebuild")
  @Operation(summary = "从业务模块公开快照原子重建统计投影")
  ReportViews.ProjectionStatus rebuild(
      @AuthenticationPrincipal StaffIdentity actor, @Valid @RequestBody Rebuild body) {
    return maintenance.rebuild(actor, body.version());
  }

  /** 重建是有并发影响的资源变更，必须提供控制版本. */
  record Rebuild(@NotNull @Min(0) Long version) {}
}
