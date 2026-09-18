package com.hanserwei.hanmenu.catalog.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 人民币金额值对象，精确到分；定价不允许零、负数或隐式舍入. */
public record Money(BigDecimal amount) {
  /** 验证金额范围和小数精度，避免浮点计算与数据库截断. */
  public Money {
    if (amount == null
        || amount.signum() <= 0
        || amount.compareTo(new BigDecimal("999999.99")) > 0) {
      throw new CatalogException(
          CatalogException.Reason.INVALID_INPUT, "价格须在 0.01 至 999999.99 元之间");
    }
    try {
      amount = amount.setScale(2, RoundingMode.UNNECESSARY);
    } catch (ArithmeticException exception) {
      throw new CatalogException(CatalogException.Reason.INVALID_INPUT, "价格最多支持两位小数");
    }
  }
}
