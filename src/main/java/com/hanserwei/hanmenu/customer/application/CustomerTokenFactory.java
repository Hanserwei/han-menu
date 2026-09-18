package com.hanserwei.hanmenu.customer.application;

import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** 生成与员工会话隔离的顾客令牌，原始值只在登录响应中返回. */
@Component
public final class CustomerTokenFactory {
  private final SecureRandom random = new SecureRandom();

  /** 创建带顾客前缀的不透明令牌. */
  public String newToken() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return "hmc_" + BaseEncoding.base64Url().omitPadding().encode(bytes);
  }

  /** 计算数据库会话摘要. */
  public String digest(String token) {
    return Hashing.sha256().hashString(token, StandardCharsets.UTF_8).toString();
  }
}
