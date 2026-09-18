package com.hanserwei.hanmenu.customer.domain;

/** 顾客模块可预期失败，消息不包含 SQL、凭证或请求原文. */
public final class CustomerException extends RuntimeException {
  private final Reason reason;

  /** 构造顾客业务失败. */
  public CustomerException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  /** 返回供 HTTP 层映射的业务分类. */
  public Reason reason() {
    return reason;
  }

  /** 顾客输入、认证、资源、冲突和基础设施失败分类. */
  public enum Reason {
    INVALID_INPUT,
    INVALID_CREDENTIALS,
    NOT_FOUND,
    CONFLICT,
    VERSION_CONFLICT,
    RATE_LIMITED,
    UNAVAILABLE
  }
}
