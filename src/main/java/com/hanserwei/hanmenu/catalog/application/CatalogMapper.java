package com.hanserwei.hanmenu.catalog.application;

import com.hanserwei.hanmenu.catalog.api.CatalogViews;
import com.hanserwei.hanmenu.catalog.domain.Category;
import com.hanserwei.hanmenu.catalog.domain.MenuProduct;

/** 集中完成领域到查询契约的转换，避免接口层直接序列化聚合. */
final class CatalogMapper {
  private CatalogMapper() {}

  static CatalogViews.CategoryView category(Category value) {
    return new CatalogViews.CategoryView(
        value.id(),
        value.kind().name(),
        value.name(),
        value.sortOrder(),
        value.enabled(),
        value.version());
  }

  static CatalogViews.ProductView product(MenuProduct value) {
    return new CatalogViews.ProductView(
        value.id(),
        value.kind().name(),
        value.categoryId(),
        value.name(),
        value.description(),
        value.price().amount(),
        "CNY",
        value.imageId(),
        value.flavors().stream()
            .map(
                group ->
                    new CatalogViews.FlavorView(group.name(), group.options(), group.required()))
            .toList(),
        value.components().stream()
            .map(
                part ->
                    new CatalogViews.ComponentView(
                        part.dishId(), part.quantity(), part.selections()))
            .toList(),
        value.onSale() ? "ON_SALE" : "OFF_SALE",
        value.version(),
        value.createdAt());
  }
}
