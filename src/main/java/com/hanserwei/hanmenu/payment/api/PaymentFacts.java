package com.hanserwei.hanmenu.payment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 已确认资金事实的统计与重建契约，不导出渠道报文、密钥或个人资料. */
public interface PaymentFacts {
  /** 分批读取已确认收款，每批最多二百条. */
  List<Receipt> receiptsAfter(UUID cursor, int limit);

  /** 分批读取已确认退款，不把受理状态算入退款金额. */
  List<Refund> refundsAfter(UUID cursor, int limit);

  /** 原收款以渠道付款时刻计入资金日账. */
  record Receipt(UUID id, UUID orderId, BigDecimal amount, Instant paidAt) {}

  /** 退款以服务端确认时刻计入资金日账. */
  record Refund(UUID id, UUID paymentId, UUID orderId, BigDecimal amount, Instant confirmedAt) {}
}
