package com.hanserwei.hanmenu.identity.domain;

/** 密码摘要端口，具体密码算法由基础设施提供，领域模型不接触 Spring Security. */
public interface PasswordHasher {
  /** 对已验证的新密码生成自适应摘要. */
  String encode(NewPassword password);

  /** 比较登录密码和摘要；摘要缺失时仍执行等成本的无效比较以减少账号枚举差异. */
  boolean matches(String rawPassword, String encodedPassword);
}
