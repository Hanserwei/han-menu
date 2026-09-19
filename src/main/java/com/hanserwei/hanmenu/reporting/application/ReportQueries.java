package com.hanserwei.hanmenu.reporting.application;

import com.hanserwei.hanmenu.catalog.api.CatalogSummary;
import com.hanserwei.hanmenu.identity.api.StaffAuthorization;
import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import com.hanserwei.hanmenu.reporting.domain.BusinessTime;
import com.hanserwei.hanmenu.reporting.domain.ProjectionState;
import com.hanserwei.hanmenu.reporting.domain.ReportData;
import com.hanserwei.hanmenu.reporting.domain.ReportPeriod;
import com.hanserwei.hanmenu.reporting.domain.ReportWorkbook;
import com.hanserwei.hanmenu.reporting.domain.ReportingException;
import com.hanserwei.hanmenu.reporting.domain.ReportingRepository;
import com.hanserwei.hanmenu.shop.api.ShopQuery;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 所有报表组成在同一个只读一致性快照中读取，不能混合重建前后的代际. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ReportQueries {
  private final ReportingRepository repository;
  private final StaffAuthorization authorization;
  private final CatalogSummary catalog;
  private final ShopQuery shop;
  private final Clock clock;

  /** 通过公开契约读取工作台的实时门店与目录摘要. */
  public ReportQueries(
      ReportingRepository repository,
      StaffAuthorization authorization,
      CatalogSummary catalog,
      ShopQuery shop,
      Clock clock) {
    this.repository = repository;
    this.authorization = authorization;
    this.catalog = catalog;
    this.shop = shop;
    this.clock = clock;
  }

  /** 读取有界报表快照，管理员授权发生在任何资金查询之前. */
  public ReportWorkbook load(StaffIdentity actor, ReportPeriod period, int salesLimit) {
    authorization.requireAdministrator(actor);
    if (salesLimit < 1 || salesLimit > 100) {
      throw new ReportingException(ReportingException.Reason.INVALID_INPUT, "销量条数须为 1 至 100");
    }
    var state = repository.state();
    state.requireReady();
    var days = ReportData.days(period, repository.aggregate(period));
    return new ReportWorkbook(
        period,
        metadata(state),
        ReportData.summary(days),
        days,
        repository.sales(period, salesLimit),
        repository.reconcile(period));
  }

  /** 即使投影未就绪，管理员仍可查看状态并发起修复. */
  public ReportViews.ProjectionStatus projection(StaffIdentity actor) {
    authorization.requireAdministrator(actor);
    return ReportViews.projection(repository.state(), repository.counts());
  }

  /** 普通员工可查看待办，财务统计仍要求管理员. */
  public ReportViews.Workspace workspace(StaffIdentity actor) {
    authorization.requireStaff(actor);
    var state = repository.state();
    state.requireReady();
    var statuses = repository.statuses();
    var date = BusinessTime.date(clock.instant());
    var today = repository.today(date);
    var counts = catalog.counts();
    return new ReportViews.Workspace(
        date,
        BusinessTime.ZONE.getId(),
        shop.current().status(),
        ReportViews.metadata(metadata(state)),
        statuses.getOrDefault("PAID", 0L),
        statuses.getOrDefault("ACCEPTED", 0L),
        statuses.getOrDefault("DELIVERING", 0L),
        statuses.getOrDefault("CANCELLING", 0L),
        statuses.getOrDefault("REFUNDING", 0L),
        today.get("created"),
        today.get("completed"),
        counts.dishesOnSale(),
        counts.dishesOffSale(),
        counts.mealsOnSale(),
        counts.mealsOffSale());
  }

  private ReportWorkbook.Metadata metadata(ProjectionState state) {
    return new ReportWorkbook.Metadata(
        state.version(),
        state.generation(),
        state.revision(),
        state.initialized(),
        state.updatedAt(),
        state.rebuiltAt());
  }
}
