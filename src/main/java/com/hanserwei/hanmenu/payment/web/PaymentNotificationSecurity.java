package com.hanserwei.hanmenu.payment.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** 渠道通知仅开放精确 POST 路径，控制器验签负责认证，不复用顾客或员工令牌. */
@Configuration(proxyBeanMethods = false)
class PaymentNotificationSecurity {
  @Bean
  @Order(-1)
  SecurityFilterChain paymentNotifications(HttpSecurity http) throws Exception {
    return http.securityMatcher("/api/v1/payment-notifications/alipay")
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
                    .requestMatchers(HttpMethod.POST, "/api/v1/payment-notifications/alipay")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .build();
  }
}
