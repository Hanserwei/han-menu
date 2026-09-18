package com.hanserwei.hanmenu.customer.web;

import com.hanserwei.hanmenu.customer.application.CustomerAuthentication;
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

/** 将 hmc_ 顾客会话转换为 Spring Security 身份，员工令牌不能通过本过滤器. */
final class CustomerSessionAuthenticationFilter extends OncePerRequestFilter {
  private final CustomerAuthentication authentication;
  private final CustomerSecurityResponses responses;

  CustomerSessionAuthenticationFilter(
      CustomerAuthentication authentication, CustomerSecurityResponses responses) {
    this.authentication = authentication;
    this.responses = responses;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return request.getMethod().equals("POST")
        && (path.equals("/api/v1/customer/accounts") || path.equals("/api/v1/customer/sessions"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    var headers = Collections.list(request.getHeaders("Authorization"));
    if (headers.size() > 1) {
      responses.write(request, response, 400, "Authorization 请求头不能重复");
      return;
    }
    if (headers.isEmpty()) {
      chain.doFilter(request, response);
      return;
    }
    String value = headers.getFirst();
    if (!value.regionMatches(true, 0, "Bearer ", 0, 7)) {
      responses.write(request, response, 401, "登录状态已失效");
      return;
    }
    try {
      var session = authentication.authenticate(StringUtils.trimToEmpty(value.substring(7)));
      if (session.isEmpty()) {
        responses.write(request, response, 401, "登录状态已失效");
        return;
      }
      var verified = session.orElseThrow();
      var security =
          UsernamePasswordAuthenticationToken.authenticated(
              verified.identity(), null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
      security.setDetails(verified.tokenHash());
      var context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(security);
      SecurityContextHolder.setContext(context);
    } catch (DataAccessException exception) {
      responses.write(request, response, 503, "顾客认证服务暂不可用");
      return;
    }
    chain.doFilter(request, response);
  }
}
