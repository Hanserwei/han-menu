package com.hanserwei.hanmenu.notification.web;

import com.hanserwei.hanmenu.notification.application.StreamTicketService;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.OriginHandshakeInterceptor;

/** 握手仅接受一次性子协议票据；默认同源，显式来源列表禁止通配符. */
@Configuration(proxyBeanMethods = false)
@EnableWebSocket
class NotificationWebSocketConfiguration implements WebSocketConfigurer {
  private final NotificationSocket socket;
  private final StreamTicketService tickets;
  private final List<String> origins;

  NotificationWebSocketConfiguration(
      NotificationSocket socket,
      StreamTicketService tickets,
      @Value("${han-menu.notification.allowed-origins:}") String origins) {
    this.socket = socket;
    this.tickets = tickets;
    this.origins =
        Arrays.stream(origins.split(","))
            .map(String::strip)
            .filter(value -> !value.isEmpty())
            .toList();
    if (this.origins.stream()
        .anyMatch(
            value ->
                value.contains("*")
                    || !(value.startsWith("https://") || value.startsWith("http://")))) {
      throw new IllegalArgumentException("WebSocket 来源必须是明确的 HTTP/HTTPS 地址");
    }
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(socket, "/api/v1/notifications/stream")
        .addInterceptors(new OriginHandshakeInterceptor(origins), new TicketHandshake(tickets))
        .setAllowedOrigins(origins.toArray(String[]::new));
  }

  @Bean
  @Order(-2)
  SecurityFilterChain notificationStream(HttpSecurity http) throws Exception {
    return http.securityMatcher("/api/v1/notifications/stream")
        .csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            rules ->
                rules
                    .requestMatchers(
                        org.springframework.http.HttpMethod.GET, "/api/v1/notifications/stream")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .build();
  }

  /** 在升级前消费票据，URI 参数不能携带凭证，响应只协商固定业务子协议. */
  private static final class TicketHandshake implements HandshakeInterceptor {
    private final StreamTicketService tickets;

    TicketHandshake(StreamTicketService tickets) {
      this.tickets = tickets;
    }

    @Override
    public boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler handler,
        Map<String, Object> attributes) {
      if (request.getURI().getRawQuery() != null) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
      }
      var protocols =
          request.getHeaders().getOrEmpty("Sec-WebSocket-Protocol").stream()
              .flatMap(value -> Arrays.stream(value.split(",")))
              .map(String::strip)
              .toList();
      var secrets = protocols.stream().filter(value -> value.startsWith("ticket.")).toList();
      if (!protocols.contains(NotificationSocket.PROTOCOL) || secrets.size() != 1) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
      }
      try {
        attributes.put(NotificationSocket.PROOF, tickets.consume(secrets.getFirst().substring(7)));
        return true;
      } catch (com.hanserwei.hanmenu.notification.domain.NotificationException exception) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
      } catch (RuntimeException exception) {
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return false;
      }
    }

    @Override
    public void afterHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler handler,
        Exception exception) {}
  }
}
