package com.hanserwei.hanmenu.customer.domain;

import java.util.List;

/** 顾客聚合的有界查询结果，仅供本模块应用层映射. */
public record CustomerPage(List<CustomerAccount> items, long totalElements) {
  /** 固定查询集合，避免调用者修改仓储结果. */
  public CustomerPage {
    items = List.copyOf(items);
  }
}
