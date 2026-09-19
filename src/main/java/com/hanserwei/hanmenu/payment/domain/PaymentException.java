package com.hanserwei.hanmenu.payment.domain;

/** 支付模块稳定错误分类，不包含渠道原始报文或密钥. */
public final class PaymentException extends RuntimeException {
  private final Reason reason;

  /** 记录可公开的错误分类与说明. */
  public PaymentException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  /** 返回业务错误分类. */
  public Reason reason() {
    return reason;
  }

  /** 区分参数、归属、版本、状态及渠道故障. */
  public enum Reason {
    INVALID_INPUT,
    NOT_FOUND,
    VERSION_CONFLICT,
    CONFLICT,
    INVALID_NOTIFICATION,
    UNAVAILABLE
  }
}
