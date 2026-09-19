package com.hanserwei.hanmenu.catalog.api;

/** 员工工作台使用的目录规模摘要，不导出商品或持久化对象. */
public interface CatalogSummary {
  /** 实时读取菜品和套餐上下架数量，不以展示缓存控制业务状态. */
  Counts counts();

  /** 单店工作台目录数量. */
  record Counts(long dishesOnSale, long dishesOffSale, long mealsOnSale, long mealsOffSale) {}
}
