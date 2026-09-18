package com.hanserwei.hanmenu.customer.infrastructure;

import com.google.common.hash.Hashing;
import com.hanserwei.hanmenu.customer.domain.CustomerAttemptLimiter;
import com.hanserwei.hanmenu.customer.domain.CustomerException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** 对账号和直连地址分别进行原子限流，防止轮换用户名或来源绕过单一计数. */
@Component
class RedisCustomerAttemptLimiter implements CustomerAttemptLimiter {
  private static final DefaultRedisScript<Long> SCRIPT =
      new DefaultRedisScript<>(
          """
          local counts = {}
          for index, key in ipairs(KEYS) do
            counts[index] = redis.call('INCR', key)
            if counts[index] == 1 or redis.call('PTTL', key) < 0 then
              redis.call('PEXPIRE', key, ARGV[1])
            end
          end
          if counts[1] > tonumber(ARGV[2]) or counts[2] > tonumber(ARGV[3]) then
            return 0
          end
          return 1
          """,
          Long.class);
  private final StringRedisTemplate redis;
  private final String prefix;
  private final int attempts;
  private final int ipAttempts;
  private final Duration window;

  RedisCustomerAttemptLimiter(
      StringRedisTemplate redis,
      @Value("${han-menu.customer.redis-key-prefix}") String prefix,
      @Value("${han-menu.customer.login-max-attempts}") int attempts,
      @Value("${han-menu.customer.login-max-ip-attempts}") int ipAttempts,
      @Value("${han-menu.customer.login-window}") Duration window) {
    if (!prefix.startsWith("han-menu:")
        || attempts < 1
        || ipAttempts < 1
        || window.compareTo(Duration.ofSeconds(1)) < 0) {
      throw new IllegalArgumentException("注册或登录限流配置不合法");
    }
    this.redis = redis;
    this.prefix = prefix;
    this.attempts = attempts;
    this.ipAttempts = ipAttempts;
    this.window = window;
  }

  @Override
  public void check(String username, String clientAddress) {
    String accountKey = prefix + "account:" + digest(username);
    String ipKey = prefix + "ip:" + digest(clientAddress);
    Long count;
    try {
      count =
          redis.execute(
              SCRIPT,
              List.of(accountKey, ipKey),
              Long.toString(window.toMillis()),
              Integer.toString(attempts),
              Integer.toString(ipAttempts));
    } catch (DataAccessException exception) {
      throw new CustomerException(CustomerException.Reason.UNAVAILABLE, "注册或登录服务暂不可用，请稍后重试");
    }
    if (count == null) {
      throw new CustomerException(CustomerException.Reason.UNAVAILABLE, "注册或登录服务暂不可用，请稍后重试");
    }
    if (count == 0) {
      throw new CustomerException(CustomerException.Reason.RATE_LIMITED, "注册或登录请求过于频繁，请稍后重试");
    }
  }

  /** 键中不出现用户名和 IP 原文，避免基础设施日志暴露注册或登录输入. */
  private String digest(String value) {
    return Hashing.sha256().hashString(value, StandardCharsets.UTF_8).toString();
  }
}
