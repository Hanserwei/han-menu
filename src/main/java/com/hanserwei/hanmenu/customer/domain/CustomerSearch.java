package com.hanserwei.hanmenu.customer.domain;

import java.time.Instant;

/** 管理员顾客查询条件，手机号精确匹配，名称按字面包含匹配. */
public record CustomerSearch(
    String phone, String name, Boolean enabled, Instant from, Instant to, int page, int size) {
  /** 校验有界分页与注册时刻区间，规范化手机号和名称. */
  public CustomerSearch {
    if (page < 0
        || page > 10000
        || size < 1
        || size > 50
        || (from != null && to != null && !from.isBefore(to))) {
      throw new CustomerException(CustomerException.Reason.INVALID_INPUT, "分页或时间范围不合法");
    }
    if (phone != null) {
      phone = CustomerAccount.normalizePhone(phone);
    }
    if (name != null) {
      name = name.strip();
      if (name.isEmpty() || name.length() > 50) {
        throw new CustomerException(CustomerException.Reason.INVALID_INPUT, "名称筛选应为 1 至 50 个字符");
      }
    }
  }

  /** 避免查询条件在调试输出中泄露顾客资料. */
  @Override
  public String toString() {
    return "CustomerSearch[page=" + page + ", size=" + size + "]";
  }
}
