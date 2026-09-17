package com.hanserwei.hanmenu.identity.web;

import com.hanserwei.hanmenu.identity.application.EmployeeAuthentication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 将经过数据库验证的会话转换为 Spring Security 身份，不记录或保存原始请求令牌. */
final class SessionAuthenticationFilter extends OncePerRequestFilter {
  private final EmployeeAuthentication authentication;
  private final SecurityResponses responses;

  SessionAuthenticationFilter(EmployeeAuthentication authentication, SecurityResponses responses) {
    this.authentication = authentication;
    this.responses = responses;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return (path.equals("/admin/employee/login") && request.getMethod().equals("POST"))
        || path.startsWith("/actuator/health")
        || path.startsWith("/v3/api-docs")
        || path.startsWith("/swagger-ui");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    var legacy = Collections.list(request.getHeaders("token"));
    var bearer = Collections.list(request.getHeaders("Authorization"));
    if (legacy.size() > 1 || bearer.size() > 1 || (!legacy.isEmpty() && !bearer.isEmpty())) {
      responses.write(request, response, 400, "请只提供一种登录令牌");
      return;
    }
    String token = legacy.isEmpty() ? null : legacy.getFirst();
    if (!bearer.isEmpty()) {
      String value = bearer.getFirst();
      if (!value.regionMatches(true, 0, "Bearer ", 0, 7)) {
        responses.write(request, response, 401, "登录状态已失效");
        return;
      }
      token = value.substring(7);
    }
    if (token != null) {
      try {
        var session = authentication.authenticate(StringUtils.trimToEmpty(token));
        if (session.isEmpty()) {
          responses.write(request, response, 401, "登录状态已失效");
          return;
        }
        var verified = session.orElseThrow();
        var security =
            UsernamePasswordAuthenticationToken.authenticated(
                verified.identity(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + verified.identity().role())));
        security.setDetails(verified.tokenHash());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(security);
        SecurityContextHolder.setContext(context);
      } catch (DataAccessException exception) {
        responses.write(request, response, 503, "认证服务暂不可用");
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
