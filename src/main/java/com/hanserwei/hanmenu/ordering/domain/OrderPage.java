package com.hanserwei.hanmenu.ordering.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 历史订单分页不读取收货资料或延迟加载条目，避免列表查询放大. */
public record OrderPage(List<Summary> items, long totalElements) {
  /** 固定分页集合. */
  public OrderPage {
    items = List.copyOf(items);
  }

  /** 列表所需最小订单信息. */
  public record Summary(
      UUID id,
      Order.Status status,
      BigDecimal total,
      long version,
      Instant createdAt,
      Instant cancelledAt) {}
}
