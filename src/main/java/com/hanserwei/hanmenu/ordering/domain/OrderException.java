package com.hanserwei.hanmenu.ordering.domain;

/** 订单领域拒绝原因，不携带客户资料或持久化细节. */
public final class OrderException extends RuntimeException {
  private final Reason reason;

  /** 保存稳定业务分类和可公开的错误说明. */
  public OrderException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  /** 返回错误分类. */
  public Reason reason() {
    return reason;
  }

  /** 区分输入、归属、营业、状态和幂等冲突. */
  public enum Reason {
    INVALID_INPUT,
    NOT_FOUND,
    SHOP_CLOSED,
    VERSION_CONFLICT,
    STATE_CONFLICT,
    IDEMPOTENCY_CONFLICT
  }
}
