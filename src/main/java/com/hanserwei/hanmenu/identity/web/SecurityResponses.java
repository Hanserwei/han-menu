package com.hanserwei.hanmenu.identity.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 统一安全链与控制器异常的响应格式，避免未认证请求绕开旧客户端约定. */
@Component
public final class SecurityResponses {
  private final JsonMapper json;

  /** 复用应用 JSON 配置，使接口和安全链采用一致的序列化方式. */
  public SecurityResponses(JsonMapper json) {
    this.json = json;
  }

  /** 按请求边界返回旧信封或 Problem Details，调用方只能传入固定安全消息. */
  public void write(
      HttpServletRequest request, HttpServletResponse response, int status, String message)
      throws IOException {
    boolean legacy = request.getRequestURI().startsWith("/admin/");
    final Object body =
        legacy
            ? LegacyResponse.failure(message)
            : ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatusCode.valueOf(status), message);
    response.setStatus(status);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(legacy ? "application/json" : "application/problem+json");
    response.setHeader("Cache-Control", "no-store");
    response.getWriter().write(json.writeValueAsString(body));
  }
}
