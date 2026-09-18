package com.hanserwei.hanmenu.customer.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hanserwei.hanmenu.customer.domain.CustomerException;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 使用明确关闭的本机端口，验证 Redis 连接失败时限流不能静默放行. */
class RedisCustomerAttemptLimiterTest {
  @Test
  void unavailableRedisFailsClosedWithoutExposingConnectionDetails() throws Exception {
    int port;
    try (var socket = new ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    var settings = new RedisStandaloneConfiguration("127.0.0.1", port);
    var client =
        LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(200)).build();
    var connection = new LettuceConnectionFactory(settings, client);
    connection.afterPropertiesSet();
    connection.start();
    try {
      var limiter =
          new RedisCustomerAttemptLimiter(
              new StringRedisTemplate(connection),
              "han-menu:test:unavailable:",
              4,
              10,
              Duration.ofMinutes(1));
      assertThatThrownBy(() -> limiter.check("employee", "127.0.0.1"))
          .isInstanceOf(CustomerException.class)
          .hasMessage("注册或登录服务暂不可用，请稍后重试");
    } finally {
      connection.destroy();
    }
  }
}
