package com.hanserwei.hanmenu.identity.web;

import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import com.hanserwei.hanmenu.identity.application.EmployeeAuthentication;
import com.hanserwei.hanmenu.identity.domain.AuditTrail;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/** 配置默认拒绝的安全边界；仅请求头携带凭证，不使用浏览器自动提交的认证 Cookie. */
@Configuration(proxyBeanMethods = false)
class IdentitySecurityConfiguration {
  @Bean
  SecurityFilterChain applicationSecurity(
      HttpSecurity http,
      EmployeeAuthentication authentication,
      SecurityResponses responses,
      AuditTrail audit)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/admin/employee/login")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/actuator/health",
                        "/actuator/health/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/admin/employee/me")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/admin/employee/logout")
                    .authenticated()
                    .requestMatchers(HttpMethod.PUT, "/admin/employee/password")
                    .authenticated()
                    .requestMatchers("/admin/employee", "/admin/employee/**", "/actuator/info")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(
                        (request, response, exception) ->
                            responses.write(request, response, 401, "请先登录"))
                    .accessDeniedHandler(
                        (request, response, exception) -> {
                          var current = SecurityContextHolder.getContext().getAuthentication();
                          Long actor =
                              current != null
                                      && current.getPrincipal() instanceof StaffIdentity identity
                                  ? identity.employeeId()
                                  : null;
                          try {
                            audit.record(
                                AuditTrail.Action.AUTHORIZATION_DENIED, actor, null, false);
                            responses.write(request, response, 403, "没有访问权限");
                          } catch (DataAccessException failure) {
                            responses.write(request, response, 503, "服务暂不可用");
                          }
                        }))
        .addFilterBefore(new RequestIdFilter(), SecurityContextHolderFilter.class)
        .addFilterBefore(
            new SessionAuthenticationFilter(authentication, responses),
            UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  OpenAPI identityApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Han Menu 身份与员工接口")
                .version("P1")
                .description(
                    "请求头 token 与 Bearer 二选一。管理员管理员工，普通员工仅能访问本人身份、"
                        + "退出和修改密码。业务错误保留 code/msg/data，并使用真实 HTTP 错误状态。"))
        .components(
            new Components()
                .addSecuritySchemes(
                    "employeeToken",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("token")
                        .description("旧后台使用的登录令牌"))
                .addSecuritySchemes(
                    "bearerToken",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("Opaque")))
        .addSecurityItem(new SecurityRequirement().addList("employeeToken"))
        .addSecurityItem(new SecurityRequirement().addList("bearerToken"));
  }

  /** 每次请求生成服务端追踪标识，不信任客户端提供的日志内容或跨线程共享状态. */
  private static final class RequestIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
      String id = UUID.randomUUID().toString();
      response.setHeader("X-Request-ID", id);
      try (var ignored = MDC.putCloseable("requestId", id)) {
        chain.doFilter(request, response);
      }
    }
  }
}
