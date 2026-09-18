package com.hanserwei.hanmenu.catalog.domain;

import java.util.HashSet;
import java.util.List;

/** 菜品的单选口味组，名称和可选值在聚合内唯一，消费者不可修改集合. */
public record FlavorGroup(String name, List<String> options, boolean required) {
  /** 规范化口味文本并限制规模；空选项和重复选项不能保存. */
  public FlavorGroup {
    name = CatalogText.required(name, 30, "口味名称");
    if (options == null || options.isEmpty() || options.size() > 20) {
      throw new CatalogException(CatalogException.Reason.INVALID_INPUT, "每个口味组须有 1 至 20 个选项");
    }
    options = options.stream().map(value -> CatalogText.required(value, 30, "口味选项")).toList();
    if (new HashSet<>(options).size() != options.size()) {
      throw new CatalogException(CatalogException.Reason.INVALID_INPUT, "口味选项不能重复");
    }
  }
}
