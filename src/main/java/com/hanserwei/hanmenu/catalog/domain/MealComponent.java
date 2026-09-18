package com.hanserwei.hanmenu.catalog.domain;

import java.util.Map;
import java.util.UUID;

/** 套餐中一款菜品的数量和确定的口味选择，套餐本身不能嵌套其他套餐. */
public record MealComponent(UUID dishId, int quantity, Map<String, String> selections) {
  /** 拷贝规格选择，数量限制为 1 至 99；是否属于合法菜品由跨聚合策略检查. */
  public MealComponent {
    if (dishId == null
        || quantity < 1
        || quantity > 99
        || selections == null
        || selections.size() > 10) {
      throw new CatalogException(CatalogException.Reason.INVALID_INPUT, "套餐明细不合法");
    }
    selections = Map.copyOf(selections);
    selections.forEach(
        (key, value) -> {
          CatalogText.required(key, 30, "口味名称");
          CatalogText.required(value, 30, "口味选项");
        });
  }
}
