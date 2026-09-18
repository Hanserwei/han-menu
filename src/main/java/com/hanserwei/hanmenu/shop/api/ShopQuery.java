package com.hanserwei.hanmenu.shop.api;

/** 对外提供营业规则的实时快照，后续下单时应再次获取和验证. */
public interface ShopQuery {
  /** 返回门店当前信息，不将缓存状态作为营业事实来源. */
  ShopView current();

  /** 对外查询模型不含领域对象或 ORM 类型. */
  record ShopView(String name, String phone, String address, String status, long version) {}
}
