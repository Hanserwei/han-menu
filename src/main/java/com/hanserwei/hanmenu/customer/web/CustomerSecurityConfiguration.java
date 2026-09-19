package com.hanserwei.hanmenu.customer.web;

import com.hanserwei.hanmenu.customer.application.CustomerAuthentication;
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
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/** 顾客路径优先使用独立安全链，员工安全链不能为顾客路径授予权限. */
@Configuration(proxyBeanMethods = false)
class CustomerSecurityConfiguration {
  @Bean
  @Order(0)
  SecurityFilterChain customerSecurity(
      HttpSecurity http, CustomerAuthentication authentication, CustomerSecurityResponses responses)
      throws Exception {
    return http.securityMatcher(
            "/api/v1/customer/**",
            "/api/v1/cart",
            "/api/v1/cart/**",
            "/api/v1/orders",
            "/api/v1/orders/**")
        .csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            rules ->
                rules
                    .dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST, "/api/v1/customer/accounts", "/api/v1/customer/sessions")
                    .permitAll()
                    .anyRequest()
                    .hasRole("CUSTOMER"))
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(
                        (request, response, exception) ->
                            responses.write(request, response, 401, "请先登录顾客账号"))
                    .accessDeniedHandler(
                        (request, response, exception) ->
                            responses.write(request, response, 403, "没有顾客资源访问权限")))
        .addFilterBefore(new RequestIdFilter(), SecurityContextHolderFilter.class)
        .addFilterBefore(
            new CustomerSessionAuthenticationFilter(authentication, responses),
            UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  /** 顾客安全链独立生成追踪号，线程退出时清理 MDC. */
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
