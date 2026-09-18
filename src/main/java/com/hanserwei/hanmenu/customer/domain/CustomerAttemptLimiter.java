package com.hanserwei.hanmenu.customer.domain;

/** 顾客注册和登录频率限制，依赖服务故障时拒绝创建身份或会话. */
public interface CustomerAttemptLimiter {
  /** 同时限制顾客标识与直连来源，禁止信任任意转发头. */
  void check(String phone, String clientAddress);
}
