package com.hanserwei.hanmenu.payment.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 已持久化的渠道确认结果，和支付状态同事务登记；不包含个人资料或渠道原文. */
public record PaymentResult(
    UUID paymentId,
    UUID businessRef,
    UUID customerId,
    BigDecimal amount,
    String status,
    Instant paidAt,
    Instant occurredAt) {}
