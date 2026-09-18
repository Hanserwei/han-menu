package com.hanserwei.hanmenu.customer.domain;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** 顾客密码值对象，认证不依赖微信或短信服务. */
public record CustomerPassword(String value) {
  /** 限制 BCrypt 输入范围，禁止空密码和静默截断. */
  public CustomerPassword {
    Objects.requireNonNull(value, "密码不能为空");
    if (value.isBlank()
        || value.length() < 8
        || value.length() > 64
        || value.getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new CustomerException(CustomerException.Reason.INVALID_INPUT, "密码须为 8 至 64 个字符");
    }
  }

  /** 密码不能被默认字符串输出. */
  @Override
  public String toString() {
    return "CustomerPassword[已隐藏]";
  }
}
