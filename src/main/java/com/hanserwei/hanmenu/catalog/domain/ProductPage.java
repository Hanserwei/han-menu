package com.hanserwei.hanmenu.catalog.domain;

import java.util.List;

/** 仓储查询快照，不向领域及应用层暴露 ORM 分页类型. */
public record ProductPage(List<MenuProduct> items, long totalElements) {
  /** 固定分页快照. */
  public ProductPage {
    items = List.copyOf(items);
  }
}
