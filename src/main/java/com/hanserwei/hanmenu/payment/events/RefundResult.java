package com.hanserwei.hanmenu.payment.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 已确认全额退款事件，消费者按退款标识与聚合状态幂等处理. */
public record RefundResult(
    UUID refundId, UUID paymentId, UUID businessRef, BigDecimal amount, Instant confirmedAt) {}
