package com.hanserwei.hanmenu.identity.application;

import com.hanserwei.hanmenu.identity.api.StaffAuthorization;
import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import com.hanserwei.hanmenu.identity.domain.AuditQuery;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 当前管理员可检索身份安全审计，查询不能修改或删除历史事实. */
@Service
@Transactional(readOnly = true)
public class AuditAdministration {
  private final StaffAuthorization staff;
  private final AuditQuery audit;

  /** 注入当前员工权限与本模块审计查询端口. */
  public AuditAdministration(StaffAuthorization staff, AuditQuery audit) {
    this.staff = staff;
    this.audit = audit;
  }

  /** 校验管理员后映射有界审计响应. */
  public AuditPageView search(StaffIdentity actor, AuditQuery.Filter filter) {
    staff.requireAdministrator(actor);
    var result = audit.search(filter);
    return new AuditPageView(
        result.items().stream()
            .map(
                value ->
                    new AuditEntryView(
                        value.id(),
                        value.action().name(),
                        value.actorId(),
                        value.subjectId(),
                        value.successful(),
                        value.occurredAt()))
            .toList(),
        filter.page(),
        filter.size(),
        result.totalElements(),
        Math.ceilDiv(result.totalElements(), filter.size()));
  }

  /** 审计响应仅暴露事件及内部标识. */
  public record AuditEntryView(
      UUID id,
      String action,
      UUID actorId,
      UUID subjectId,
      boolean successful,
      Instant occurredAt) {}

  /** 零基审计分页响应. */
  public record AuditPageView(
      List<AuditEntryView> items, int page, int size, long totalElements, long totalPages) {
    /** 固定响应集合. */
    public AuditPageView {
      items = List.copyOf(items);
    }
  }
}
