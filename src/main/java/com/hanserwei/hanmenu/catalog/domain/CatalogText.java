package com.hanserwei.hanmenu.catalog.domain;

/** 目录值对象共用的文本约束，只在模块内部复用，不承担业务编排. */
final class CatalogText {
  private CatalogText() {}

  static String required(String value, int max, String label) {
    if (value == null || value.isBlank() || value.strip().length() > max) {
      throw new CatalogException(CatalogException.Reason.INVALID_INPUT, label + "不能为空且不能超长");
    }
    return value.strip();
  }
}
