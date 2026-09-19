package com.hanserwei.hanmenu.ordering.domain;

import java.time.Instant;
import java.util.UUID;

/** 后台订单组合条件，时间区间为创建时刻的左闭右开区间，电话匹配历史收货快照. */
public record OrderSearch(
    Order.Status status,
    UUID orderId,
    UUID customerId,
    String phone,
    Instant from,
    Instant to,
    int page,
    int size) {
  /** 验证分页、时间先后和完整电话号码；不把空字符串视作全量检索. */
  public OrderSearch {
    if (page < 0
        || page > 10000
        || size < 1
        || size > 50
        || (from != null && to != null && !from.isBefore(to))) {
      throw new OrderException(OrderException.Reason.INVALID_INPUT, "分页或时间范围不合法");
    }
    if (phone != null) {
      phone = phone.strip();
      if (!phone.matches("\\+?[1-9][0-9]{6,14}")) {
        throw new OrderException(OrderException.Reason.INVALID_INPUT, "请输入完整收货手机号");
      }
      if (!phone.startsWith("+")) {
        phone = "+" + phone;
      }
    }
  }

  /** 查询诊断不输出收货电话号码. */
  @Override
  public String toString() {
    return "OrderSearch[page=" + page + ", size=" + size + "]";
  }
}
