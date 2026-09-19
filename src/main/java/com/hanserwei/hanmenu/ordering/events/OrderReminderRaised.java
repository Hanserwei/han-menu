package com.hanserwei.hanmenu.ordering.events;

import java.time.Instant;
import java.util.UUID;

/** 顾客合法催单事实，标识由订单与催单次数决定，重复投递可安全去重. */
public record OrderReminderRaised(UUID id, UUID orderId, int count, Instant occurredAt) {}
