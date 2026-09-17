package com.hanserwei.hanmenu.identity.web.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import com.hanserwei.hanmenu.identity.application.EmployeeAdministration;
import com.hanserwei.hanmenu.identity.domain.NewPassword;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 当前员工自己的资源入口，身份只取自已认证上下文，调用方不能指定其他员工. */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "当前员工")
class CurrentEmployeeController {
  private final EmployeeAdministration administration;

  CurrentEmployeeController(EmployeeAdministration administration) {
    this.administration = administration;
  }

  @GetMapping
  @Operation(summary = "读取自己的身份")
  CurrentEmployeeView get(@AuthenticationPrincipal StaffIdentity identity) {
    return new CurrentEmployeeView(
        identity.employeeId(), identity.username(), identity.displayName(), identity.role());
  }

  @PutMapping("/password")
  @ApiResponse(responseCode = "204", description = "密码修改成功，旧会话已失效")
  @Operation(summary = "修改本人密码", description = "成功后该账号的全部旧令牌失效，必须重新创建会话")
  ResponseEntity<Void> password(
      @AuthenticationPrincipal StaffIdentity identity, @Valid @RequestBody PasswordChange body) {
    administration.changePassword(
        identity, body.currentPassword(), new NewPassword(body.newPassword()));
    return ResponseEntity.noContent().build();
  }

  /** 最小身份响应不暴露安全版本、会话摘要或其他内部字段. */
  record CurrentEmployeeView(UUID id, String username, String displayName, String role) {
    @Override
    public String toString() {
      return "CurrentEmployeeView[id=" + id + "]";
    }
  }

  /** 密码输入不回显，错误响应也不包含拒绝的原始输入. */
  record PasswordChange(
      @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank @Size(max = 128)
          String currentPassword,
      @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank @Size(min = 12, max = 64)
          String newPassword) {
    @Override
    public String toString() {
      return "PasswordChange[凭证已隐藏]";
    }
  }
}
