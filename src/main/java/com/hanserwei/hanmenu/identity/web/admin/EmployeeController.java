package com.hanserwei.hanmenu.identity.web.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import com.hanserwei.hanmenu.identity.application.EmployeeAdministration;
import com.hanserwei.hanmenu.identity.domain.EmployeeAccount;
import com.hanserwei.hanmenu.identity.domain.EmployeeProfile;
import com.hanserwei.hanmenu.identity.domain.NewPassword;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 员工资源适配器，只暴露 DTO；成功状态遵循 HTTP，修改请求必须携带聚合版本. */
@RestController
@RequestMapping("/api/v1/employees")
@Tag(name = "员工资源", description = "仅管理员可以维护员工账号")
class EmployeeController {
  private final EmployeeAdministration administration;

  EmployeeController(EmployeeAdministration administration) {
    this.administration = administration;
  }

  @PostMapping
  @ApiResponse(responseCode = "201", description = "资源创建成功")
  @Operation(summary = "创建员工", description = "初始密码必须提供；角色由服务端固定为 STAFF")
  ResponseEntity<EmployeeView> create(
      @AuthenticationPrincipal StaffIdentity actor, @Valid @RequestBody CreateEmployee body) {
    var employee =
        administration.create(
            actor,
            new EmployeeProfile(body.username(), body.displayName(), body.phone()),
            new NewPassword(body.password()));
    return ResponseEntity.created(URI.create("/api/v1/employees/" + employee.id()))
        .body(EmployeeView.from(employee));
  }

  @GetMapping
  @Operation(summary = "查询员工列表", description = "page 从 0 开始；size 默认 20，最大 100")
  EmployeePageView search(
      @AuthenticationPrincipal StaffIdentity actor,
      @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(required = false) @Size(max = 50) String name) {
    var result = administration.search(actor, StringUtils.stripToEmpty(name), page, size);
    return new EmployeePageView(
        result.items().stream().map(EmployeeView::from).toList(),
        page,
        size,
        result.totalElements(),
        Math.ceilDiv(result.totalElements(), size));
  }

  @GetMapping("/{id}")
  @Operation(summary = "读取员工资源")
  EmployeeView get(@AuthenticationPrincipal StaffIdentity actor, @PathVariable UUID id) {
    return EmployeeView.from(administration.get(actor, id));
  }

  @PutMapping("/{id}")
  @ApiResponse(responseCode = "204", description = "资料更新成功")
  @Operation(summary = "更新员工资料", description = "必须提供当前 version；不会修改角色、状态或密码")
  ResponseEntity<Void> update(
      @AuthenticationPrincipal StaffIdentity actor,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateEmployee body) {
    administration.revise(
        actor,
        id,
        new EmployeeProfile(body.username(), body.displayName(), body.phone()),
        body.version());
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{id}/status")
  @ApiResponse(responseCode = "204", description = "状态更新成功")
  @Operation(summary = "变更员工状态", description = "停用撤销旧会话；重新启用不会使旧会话复活")
  ResponseEntity<Void> status(
      @AuthenticationPrincipal StaffIdentity actor,
      @PathVariable UUID id,
      @Valid @RequestBody StatusChange body) {
    administration.changeStatus(actor, id, body.status() == Status.ACTIVE, body.version());
    return ResponseEntity.noContent().build();
  }

  /** 创建账号的完整输入，密码只写不可读，不提供调试字符串中的敏感值. */
  record CreateEmployee(
      @NotBlank @Size(max = 32) String username,
      @NotBlank @Size(max = 50) String displayName,
      @Size(max = 16) String phone,
      @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank @Size(min = 12, max = 64)
          String password) {
    @Override
    public String toString() {
      return "CreateEmployee[资料与凭证已隐藏]";
    }
  }

  /** 资源标识来自路径；不接受角色、密码等不属于资料编辑的字段. */
  record UpdateEmployee(
      @NotBlank @Size(max = 32) String username,
      @NotBlank @Size(max = 50) String displayName,
      @Size(max = 16) String phone,
      @NotNull @Min(0) Long version) {
    @Override
    public String toString() {
      return "UpdateEmployee[资料已隐藏]";
    }
  }

  /** 明确命名的状态替代魔法数字，版本号始终必填. */
  record StatusChange(@NotNull Status status, @NotNull @Min(0) Long version) {}

  /** HTTP 契约的账号状态，显式映射为领域行为的输入. */
  enum Status {
    ACTIVE,
    DISABLED
  }

  /** 稳定的分页协议，不序列化 Spring Data PageImpl 内部结构. */
  record EmployeePageView(
      List<EmployeeView> items, int page, int size, long totalElements, long totalPages) {}

  /** 资源响应使用 UUID 和 UTC 时间，不包含密码摘要或 ORM 代理. */
  record EmployeeView(
      UUID id,
      String username,
      String displayName,
      String phone,
      String role,
      Status status,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    static EmployeeView from(EmployeeAccount account) {
      var profile = account.profile();
      return new EmployeeView(
          account.id(),
          profile.username(),
          profile.displayName(),
          profile.phone(),
          account.role().name(),
          account.enabled() ? Status.ACTIVE : Status.DISABLED,
          account.version(),
          account.createdAt(),
          account.updatedAt());
    }

    @Override
    public String toString() {
      return "EmployeeView[id=" + id + "]";
    }
  }
}
