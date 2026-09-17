package com.hanserwei.hanmenu.identity.web.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import com.hanserwei.hanmenu.identity.application.EmployeeAdministration;
import com.hanserwei.hanmenu.identity.application.EmployeeAuthentication;
import com.hanserwei.hanmenu.identity.domain.EmployeeAccount;
import com.hanserwei.hanmenu.identity.domain.EmployeeProfile;
import com.hanserwei.hanmenu.identity.domain.NewPassword;
import com.hanserwei.hanmenu.identity.web.LegacyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;
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

/** 兼容旧后台的员工 HTTP 适配器，显式选择响应字段，绝不返回密码摘要. */
@RestController
@RequestMapping("/admin/employee")
@Tag(name = "员工与身份", description = "旧后台契约及新增安全能力")
class EmployeeController {
  private final EmployeeAuthentication authentication;
  private final EmployeeAdministration administration;

  EmployeeController(EmployeeAuthentication authentication, EmployeeAdministration administration) {
    this.authentication = authentication;
    this.administration = administration;
  }

  @PostMapping("/login")
  @Operation(summary = "员工登录", description = "令牌有效八小时；使用 token 或 Bearer 请求头二选一")
  @SecurityRequirements
  LegacyResponse<LoginView> login(
      @Valid @RequestBody LoginRequest body, HttpServletRequest request) {
    var result = authentication.login(body.username(), body.password(), request.getRemoteAddr());
    var identity = result.identity();
    return LegacyResponse.success(
        new LoginView(
            identity.employeeId(),
            identity.username(),
            identity.name(),
            result.token(),
            identity.role(),
            result.expiresAt()));
  }

  @PostMapping("/logout")
  @Operation(summary = "退出当前会话", description = "即时撤销当前令牌，不影响其他有效会话")
  LegacyResponse<Void> logout(
      @AuthenticationPrincipal StaffIdentity identity, Authentication session) {
    authentication.logout(identity, (String) session.getDetails());
    return LegacyResponse.success(null);
  }

  @GetMapping("/me")
  @Operation(summary = "查询自己的身份", description = "管理员和普通员工均可调用")
  LegacyResponse<CurrentEmployee> me(@AuthenticationPrincipal StaffIdentity identity) {
    return LegacyResponse.success(
        new CurrentEmployee(
            identity.employeeId(), identity.username(), identity.name(), identity.role()));
  }

  @PostMapping
  @Operation(summary = "创建普通员工", description = "仅管理员；未传密码时在响应中一次性返回随机初始密码")
  LegacyResponse<EmployeeAdministration.CreatedEmployee> create(
      @AuthenticationPrincipal StaffIdentity identity, @Valid @RequestBody CreateEmployee body) {
    return LegacyResponse.success(
        administration.create(
            identity,
            new EmployeeProfile(
                body.username(), body.name(), body.phone(), body.sex(), body.idNumber()),
            body.password()));
  }

  @GetMapping("/page")
  @Operation(summary = "员工分页查询", description = "仅管理员；page 从 1 开始，pageSize 为 1 至 100")
  LegacyResponse<EmployeePage> page(
      @AuthenticationPrincipal StaffIdentity identity,
      @RequestParam(defaultValue = "1") @Min(1) @Max(10000) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
      @RequestParam(required = false) @Size(max = 50) String name) {
    var result = administration.page(identity, StringUtils.stripToEmpty(name), page, pageSize);
    return LegacyResponse.success(
        new EmployeePage(
            result.total(), result.records().stream().map(EmployeeView::from).toList()));
  }

  @GetMapping("/{id}")
  @Operation(summary = "查询员工资料", description = "仅管理员；响应不含密码或密码摘要")
  LegacyResponse<EmployeeView> get(
      @AuthenticationPrincipal StaffIdentity identity, @PathVariable @Positive long id) {
    return LegacyResponse.success(EmployeeView.from(administration.get(identity, id)));
  }

  @PutMapping
  @Operation(summary = "更新员工资料", description = "仅管理员；不支持通过资料请求变更角色、状态或密码")
  LegacyResponse<Void> update(
      @AuthenticationPrincipal StaffIdentity identity, @Valid @RequestBody UpdateEmployee body) {
    administration.revise(
        identity,
        body.id(),
        new EmployeeProfile(
            body.username(), body.name(), body.phone(), body.sex(), body.idNumber()),
        body.version());
    return LegacyResponse.success(null);
  }

  @GetMapping("/status/{status}")
  @Operation(
      summary = "兼容旧后台的启停用",
      deprecated = true,
      description = "仅管理员；旧 GET 写操作仅作为兼容入口，推荐迁移至 PATCH")
  LegacyResponse<Void> legacyStatus(
      @AuthenticationPrincipal StaffIdentity identity,
      @PathVariable @Min(0) @Max(1) int status,
      @RequestParam @Positive long id,
      HttpServletResponse response) {
    response.setHeader("Deprecation", "true");
    response.setHeader("Cache-Control", "no-store");
    administration.changeStatus(identity, id, status == 1, null);
    return LegacyResponse.success(null);
  }

  @PatchMapping("/{id}/status")
  @Operation(summary = "启停用员工", description = "仅管理员；停用后旧令牌即时失效，重新启用不会恢复旧令牌")
  LegacyResponse<Void> changeStatus(
      @AuthenticationPrincipal StaffIdentity identity,
      @PathVariable @Positive long id,
      @Valid @RequestBody StatusChange body) {
    administration.changeStatus(identity, id, body.status() == 1, body.version());
    return LegacyResponse.success(null);
  }

  @PutMapping("/password")
  @Operation(summary = "修改自己的密码", description = "要求旧密码；成功后全部旧令牌失效，需要重新登录")
  LegacyResponse<Void> changePassword(
      @AuthenticationPrincipal StaffIdentity identity, @Valid @RequestBody PasswordChange body) {
    administration.changePassword(
        identity, body.oldPassword(), new NewPassword(body.newPassword()));
    return LegacyResponse.success(null);
  }

  /** 登录请求不允许默认字符串表示输出凭证. */
  record LoginRequest(
      @NotBlank @Size(min = 3, max = 32) String username,
      @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank @Size(max = 128)
          String password) {
    @Override
    public String toString() {
      return "LoginRequest[凭证已隐藏]";
    }
  }

  /** 保留 id、userName、name、token，新增字段不改变旧客户端读取方式. */
  record LoginView(
      long id, String userName, String name, String token, String role, Instant expiresAt) {
    @Override
    public String toString() {
      return "LoginView[token=已隐藏]";
    }
  }

  /** 创建请求的角色由服务端固定，密码未传时生成随机值；个人资料不进入默认日志. */
  record CreateEmployee(
      @NotBlank @Size(max = 32) String username,
      @NotBlank @Size(max = 50) String name,
      @Size(max = 11) String phone,
      @Size(max = 1) String sex,
      @Size(max = 18) String idNumber,
      @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @Size(min = 12, max = 64)
          String password) {
    @Override
    public String toString() {
      return "CreateEmployee[资料与凭证已隐藏]";
    }
  }

  /** 资料编辑不携带凭证与角色，版本可由支持并发编辑保护的新客户端提供. */
  record UpdateEmployee(
      @NotNull @Positive Long id,
      @NotBlank @Size(max = 32) String username,
      @NotBlank @Size(max = 50) String name,
      @Size(max = 11) String phone,
      @Size(max = 1) String sex,
      @Size(max = 18) String idNumber,
      @Min(0) Long version) {
    @Override
    public String toString() {
      return "UpdateEmployee[资料已隐藏]";
    }
  }

  /** 只允许状态 0 和 1，避免把未定义状态隐式转换为停用. */
  record StatusChange(@NotNull @Min(0) @Max(1) Integer status, @Min(0) Long version) {}

  /** 修改密码的输入不会通过字符串表示或 JSON 输出再次暴露. */
  record PasswordChange(
      @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank @Size(max = 128)
          String oldPassword,
      @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank @Size(min = 12, max = 64)
          String newPassword) {
    @Override
    public String toString() {
      return "PasswordChange[凭证已隐藏]";
    }
  }

  /** 分页信封保留旧 total 和 records 字段. */
  record EmployeePage(long total, List<EmployeeView> records) {}

  /** 自己的身份视图不返回会话撤销版本或其他安全链内部字段. */
  record CurrentEmployee(long id, String userName, String name, String role) {
    @Override
    public String toString() {
      return "CurrentEmployee[id=" + id + "]";
    }
  }

  /** 仅映射员工管理所需字段；时间按旧后台约定在上海时区显示至分钟. */
  record EmployeeView(
      long id,
      String username,
      String name,
      String phone,
      String sex,
      String idNumber,
      int status,
      String role,
      long version,
      @JsonFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime createTime,
      @JsonFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime updateTime) {
    static EmployeeView from(EmployeeAccount account) {
      var profile = account.profile();
      var zone = ZoneId.of("Asia/Shanghai");
      return new EmployeeView(
          account.id(),
          profile.username(),
          profile.name(),
          profile.phone(),
          profile.sex(),
          profile.idNumber(),
          account.enabled() ? 1 : 0,
          account.role().name(),
          account.version(),
          LocalDateTime.ofInstant(account.createdAt(), zone),
          LocalDateTime.ofInstant(account.updatedAt(), zone));
    }

    @Override
    public String toString() {
      return "EmployeeView[id=" + id + ", 资料已隐藏]";
    }
  }
}
