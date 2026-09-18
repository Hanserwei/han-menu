package com.hanserwei.hanmenu.catalog.domain;

/** 分类和商品的业务种类，创建后不可改变，防止绕过套餐与菜品约束. */
public enum ProductKind {
  DISH,
  SET_MEAL
}
