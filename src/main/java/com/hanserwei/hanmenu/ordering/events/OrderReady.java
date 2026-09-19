package com.hanserwei.hanmenu.ordering.events;

import java.time.Instant;
import java.util.UUID;

/** 只有真实付款使订单进入待接单状态时才产生来单事实，取消后迟到付款不产生来单. */
public record OrderReady(UUID id, UUID orderId, Instant occurredAt) {}
