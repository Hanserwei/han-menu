package com.hanserwei.hanmenu.customer.domain;

/** 顾客密码摘要端口，具体 BCrypt 适配器不进入领域模型. */
public interface CustomerPasswordHasher {
  /** 生成密码摘要. */
  String encode(CustomerPassword password);

  /** 比较原始密码和摘要. */
  boolean matches(String rawPassword, String encodedPassword);
}
