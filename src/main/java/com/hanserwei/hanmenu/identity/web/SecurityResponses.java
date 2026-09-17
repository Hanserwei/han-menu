package com.hanserwei.hanmenu.identity.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 安全过滤链和 MVC 统一使用 RFC 9457 错误协议，不暴露内部异常或请求凭证. */
@Component
public final class SecurityResponses {
  private final JsonMapper json;

  /** 复用应用的 JSON 序列化配置. */
  public SecurityResponses(JsonMapper json) {
    this.json = json;
  }

  /** 根据 HTTP 状态生成稳定的技术错误分类，业务失败可以使用显式分类重载. */
  public void write(
      HttpServletRequest request, HttpServletResponse response, int status, String message)
      throws IOException {
    String code =
        switch (status) {
          case 400 -> "INVALID_REQUEST";
          case 401 -> "UNAUTHENTICATED";
          case 403 -> "FORBIDDEN";
          case 404 -> "NOT_FOUND";
          case 405 -> "METHOD_NOT_ALLOWED";
          case 406 -> "NOT_ACCEPTABLE";
          case 409 -> "CONFLICT";
          case 415 -> "UNSUPPORTED_MEDIA_TYPE";
          case 429 -> "RATE_LIMITED";
          case 503 -> "UNAVAILABLE";
          default -> status >= 500 ? "INTERNAL_ERROR" : "REQUEST_REJECTED";
        };
    write(request, response, status, code, message);
  }

  /** 输出可供客户端分支处理的错误码及请求追踪标识，detail 仅接受固定安全消息. */
  public void write(
      HttpServletRequest request,
      HttpServletResponse response,
      int status,
      String code,
      String message)
      throws IOException {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), message);
    problem.setType(
        URI.create("urn:han-menu:problem:" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("code", code);
    String traceId = response.getHeader("X-Request-ID");
    if (traceId == null) {
      traceId = UUID.randomUUID().toString();
    }
    problem.setProperty("traceId", traceId);
    response.setStatus(status);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType("application/problem+json");
    response.setHeader("X-Request-ID", traceId);
    response.setHeader("Cache-Control", "no-store");
    response.getWriter().write(json.writeValueAsString(problem));
  }
}
