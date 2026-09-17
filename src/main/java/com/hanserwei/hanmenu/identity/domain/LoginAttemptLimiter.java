package com.hanserwei.hanmenu.identity.domain;

/** 登录频率控制端口，不可用时必须拒绝登录而不是绕过限流. */
public interface LoginAttemptLimiter {
  /** 分别限制账号及直连客户端地址的请求量，任一超额时拒绝登录. */
  void check(String username, String clientAddress);
}
