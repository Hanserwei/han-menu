package com.hanserwei.hanmenu.shop.domain;

/** 门店规则失败，不依赖 Web 状态码. */
public final class ShopException extends RuntimeException {
  private final Reason reason;

  /** 构造安全的业务失败. */
  public ShopException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  /** 返回业务分类. */
  public Reason reason() {
    return reason;
  }

  /** 门店目前可能出现的规则失败. */
  public enum Reason {
    INVALID_INPUT,
    CONFLICT,
    VERSION_CONFLICT
  }
}
