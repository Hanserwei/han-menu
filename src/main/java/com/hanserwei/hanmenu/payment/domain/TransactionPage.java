package com.hanserwei.hanmenu.payment.domain;

import java.util.List;

/** 支付模块有界查询结果，泛型仅用于同模块支付和退款集合. */
public record TransactionPage<T>(List<T> items, long totalElements) {
  /** 固定查询集合. */
  public TransactionPage {
    items = List.copyOf(items);
  }
}
