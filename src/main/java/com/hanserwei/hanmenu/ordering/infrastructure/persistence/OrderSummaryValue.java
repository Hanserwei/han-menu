package com.hanserwei.hanmenu.ordering.infrastructure.persistence;

import com.hanserwei.hanmenu.ordering.domain.Order;
import com.hanserwei.hanmenu.ordering.domain.OrderPage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 历史派生查询的构造投影，只选择摘要列，不加载地址或订单实体. */
record OrderSummaryValue(
    UUID id,
    Order.Status status,
    BigDecimal total,
    long version,
    Instant createdAt,
    Instant cancelledAt) {
  OrderPage.Summary domain() {
    return new OrderPage.Summary(id, status, total, version, createdAt, cancelledAt);
  }
}
