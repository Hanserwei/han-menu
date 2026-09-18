package com.hanserwei.hanmenu.catalog.api;

import java.util.UUID;

/** 供购物车及订单阶段使用的商品查询契约，下单校验应读取实时事实而非缓存展示数据. */
public interface CatalogQuery {
  /** 读取当前可售商品，不存在或已下架时拒绝，结果不可用于绕过后续下单时的重新校验. */
  CatalogViews.ProductView availableProduct(UUID id);
}
