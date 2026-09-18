package com.hanserwei.hanmenu.catalog.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 目录公开的不可变查询模型，不能包含领域聚合、ORM 实体或临时签名 URL. */
public final class CatalogViews {
  private CatalogViews() {}

  /** 分类的当前快照. */
  public record CategoryView(
      UUID id, String kind, String name, int sortOrder, boolean enabled, long version) {}

  /** 菜品可选口味，公开 DTO 与领域值对象独立. */
  public record FlavorView(String name, List<String> options, boolean required) {
    /** 固定公开口味快照，缓存反序列化后仍不能由调用方修改. */
    public FlavorView {
      options = List.copyOf(options);
    }
  }

  /** 套餐组成快照，选择已经过菜品规则验证. */
  public record ComponentView(UUID dishId, int quantity, Map<String, String> selections) {
    /** 固定套餐中的口味选择. */
    public ComponentView {
      selections = Map.copyOf(selections);
    }
  }

  /** 商品查询快照，价格以 CNY 元为单位，图片使用独立资源标识. */
  public record ProductView(
      UUID id,
      String kind,
      UUID categoryId,
      String name,
      String description,
      BigDecimal price,
      String currency,
      UUID imageId,
      List<FlavorView> flavors,
      List<ComponentView> components,
      String status,
      long version,
      Instant createdAt) {
    /** 固定聚合查询结果，不允许外部修改列表. */
    public ProductView {
      flavors = List.copyOf(flavors);
      components = List.copyOf(components);
    }
  }

  /** 零基分页响应，不暴露 Spring Data 内部类型. */
  public record ProductPage(
      List<ProductView> items, int page, int size, long totalElements, long totalPages) {
    /** 固定分页快照，并拒绝空集合字段等损坏缓存结构. */
    public ProductPage {
      items = List.copyOf(items);
    }
  }
}
