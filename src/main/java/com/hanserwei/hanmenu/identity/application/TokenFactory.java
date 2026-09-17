package com.hanserwei.hanmenu.identity.application;

import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** 生成不可预测的会话令牌，持久化仅使用摘要. */
@Component
public final class TokenFactory {
  private final SecureRandom random = new SecureRandom();

  /** 返回 256 位随机令牌，并用固定前缀与后续顾客身份令牌区分. */
  public String newToken() {
    return "hme_" + randomSecret(32);
  }

  /** 计算摘要；原始令牌不能进入仓储、审计日志或查询条件日志. */
  public String digest(String token) {
    return Hashing.sha256().hashString(token, StandardCharsets.UTF_8).toString();
  }

  private String randomSecret(int length) {
    byte[] bytes = new byte[length];
    random.nextBytes(bytes);
    return BaseEncoding.base64Url().omitPadding().encode(bytes);
  }
}
