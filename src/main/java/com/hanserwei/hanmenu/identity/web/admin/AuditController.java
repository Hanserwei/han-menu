package com.hanserwei.hanmenu.identity.web.admin;

import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import com.hanserwei.hanmenu.identity.application.AuditAdministration;
import com.hanserwei.hanmenu.identity.domain.AuditQuery;
import com.hanserwei.hanmenu.identity.domain.AuditTrail;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理员身份安全审计检索，不开放修改和删除接口. */
@RestController
@RequestMapping("/api/v1/management/audit-events")
@Tag(name = "管理员安全审计")
class AuditController {
  private final AuditAdministration administration;

  AuditController(AuditAdministration administration) {
    this.administration = administration;
  }

  /** 按固定事件、操作者、目标和发生时刻检索审计记录. */
  @GetMapping
  @Operation(summary = "分页检索身份安全审计")
  AuditAdministration.AuditPageView search(
      @AuthenticationPrincipal StaffIdentity actor,
      @RequestParam(required = false) AuditTrail.Action action,
      @RequestParam(required = false) UUID actorId,
      @RequestParam(required = false) UUID subjectId,
      @RequestParam(required = false) Boolean successful,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return administration.search(
        actor, new AuditQuery.Filter(action, actorId, subjectId, successful, from, to, page, size));
  }
}
