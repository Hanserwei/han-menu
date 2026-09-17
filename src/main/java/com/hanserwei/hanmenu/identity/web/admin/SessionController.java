package com.hanserwei.hanmenu.identity.web.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import com.hanserwei.hanmenu.identity.application.EmployeeAuthentication;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 员工会话资源，通过创建和删除表达登录及退出，不维护任何旧协议别名. */
@RestController
@RequestMapping("/api/v1/sessions")
@Tag(name = "员工会话")
class SessionController {
  private final EmployeeAuthentication authentication;

  SessionController(EmployeeAuthentication authentication) {
    this.authentication = authentication;
  }

  @PostMapping
  @ApiResponse(responseCode = "201", description = "资源创建成功")
  @Operation(summary = "创建员工会话", description = "返回的 accessToken 只在本次响应中可见")
  @SecurityRequirements
  ResponseEntity<SessionView> create(
      @Valid @RequestBody Credentials body, HttpServletRequest request) {
    var result = authentication.login(body.username(), body.password(), request.getRemoteAddr());
    return ResponseEntity.created(URI.create("/api/v1/sessions/current"))
        .body(new SessionView(result.token(), "Bearer", result.expiresAt()));
  }

  @DeleteMapping("/current")
  @ApiResponse(responseCode = "204", description = "当前会话已撤销")
  @Operation(summary = "撤销当前会话", description = "当前令牌立即失效，其他会话不受影响")
  ResponseEntity<Void> delete(
      @AuthenticationPrincipal StaffIdentity identity, Authentication context) {
    authentication.logout(identity, (String) context.getDetails());
    return ResponseEntity.noContent().build();
  }

  /** 登录输入不允许通过默认字符串表示泄露凭证. */
  record Credentials(
      @NotBlank @Size(min = 3, max = 32) String username,
      @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank @Size(max = 128)
          String password) {
    @Override
    public String toString() {
      return "Credentials[已隐藏]";
    }
  }

  /** 会话表示包含标准 Bearer 类型、一次性返回的令牌与过期时刻. */
  record SessionView(String accessToken, String tokenType, Instant expiresAt) {
    @Override
    public String toString() {
      return "SessionView[令牌已隐藏]";
    }
  }
}
