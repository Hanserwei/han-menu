package com.hanserwei.hanmenu.payment.domain;

import java.time.Instant;
import java.util.UUID;

/** 管理员交易检索条件，业务引用为订单 UUID，时间按交易意图创建时刻筛选. */
public record TransactionSearch(
    UUID orderId, UUID customerId, UUID paymentId, Instant from, Instant to, int page, int size) {
  /** 限制分页并验证左闭右开时间区间. */
  public TransactionSearch {
    if (page < 0
        || page > 10000
        || size < 1
        || size > 50
        || (from != null && to != null && !from.isBefore(to))) {
      throw new PaymentException(PaymentException.Reason.INVALID_INPUT, "分页或时间范围不合法");
    }
  }
}
