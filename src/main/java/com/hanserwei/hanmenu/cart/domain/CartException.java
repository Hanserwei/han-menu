package com.hanserwei.hanmenu.cart.domain;

/** 购物车业务失败，包含商品不可用、版本冲突和顾客输入错误. */
public final class CartException extends RuntimeException {
  private final Reason reason;

  /** 构造购物车业务失败. */
  public CartException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  /** 返回错误分类. */
  public Reason reason() {
    return reason;
  }

  /** 购物车错误分类. */
  public enum Reason {
    INVALID_INPUT,
    NOT_FOUND,
    CONFLICT,
    VERSION_CONFLICT,
    UNAVAILABLE
  }
}
