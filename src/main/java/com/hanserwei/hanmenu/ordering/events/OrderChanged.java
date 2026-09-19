package com.hanserwei.hanmenu.ordering.events;

import com.hanserwei.hanmenu.ordering.api.OrderFacts;

/** 同事务登记完整统计快照，消费者用订单版本拒绝重复和乱序事件. */
public record OrderChanged(OrderFacts.Snapshot snapshot) {}
