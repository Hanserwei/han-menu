package com.hanserwei.hanmenu.catalog.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 可售商品聚合，共享定价和上下架行为，并按种类保护菜品/套餐各自的不变量.
 *
 * <p>菜品维护口味组，套餐维护组成菜品和固定口味。所有内容编辑要求先下架，避免已售快照语义不清。
 */
public final class MenuProduct {
  private final UUID id;
  private final ProductKind kind;
  private UUID categoryId;
  private String name;
  private String description;
  private Money price;
  private UUID imageId;
  private List<FlavorGroup> flavors;
  private List<MealComponent> components;
  private boolean onSale;
  private final long version;
  private final Instant createdAt;

  /** 从已验证的存储快照构建聚合，重建仍执行内容约束. */
  public MenuProduct(
      UUID id,
      ProductKind kind,
      UUID categoryId,
      String name,
      String description,
      Money price,
      UUID imageId,
      List<FlavorGroup> flavors,
      List<MealComponent> components,
      boolean onSale,
      long version,
      Instant createdAt) {
    this.id = Objects.requireNonNull(id);
    this.kind = Objects.requireNonNull(kind);
    this.createdAt = Objects.requireNonNull(createdAt);
    revise(categoryId, name, description, price, imageId, flavors, components);
    if (version < 0) {
      throw new IllegalArgumentException("版本不可为负");
    }
    this.version = version;
    this.onSale = onSale;
  }

  /** 编辑下架商品；菜品和套餐种类不可在编辑中转换. */
  public void revise(
      UUID categoryId,
      String name,
      String description,
      Money price,
      UUID imageId,
      List<FlavorGroup> flavors,
      List<MealComponent> components) {
    if (onSale) {
      throw new CatalogException(CatalogException.Reason.CONFLICT, "请先下架再编辑商品");
    }
    if (categoryId == null
        || description == null
        || description.length() > 1000
        || flavors == null
        || components == null
        || flavors.size() > 10
        || components.size() > 50) {
      throw new CatalogException(CatalogException.Reason.INVALID_INPUT, "商品资料不合法");
    }
    this.name = CatalogText.required(name, 100, "商品名称");
    if (kind == ProductKind.DISH && !components.isEmpty()
        || kind == ProductKind.SET_MEAL && (!flavors.isEmpty() || components.isEmpty())) {
      throw new CatalogException(
          CatalogException.Reason.INVALID_INPUT, "菜品不能含套餐明细，套餐必须有明细且不直接定义口味");
    }
    if (new HashSet<>(flavors.stream().map(FlavorGroup::name).toList()).size() != flavors.size()
        || new HashSet<>(components.stream().map(MealComponent::dishId).toList()).size()
            != components.size()) {
      throw new CatalogException(CatalogException.Reason.INVALID_INPUT, "口味组或组成菜品不能重复");
    }
    this.categoryId = categoryId;
    this.description = description.strip();
    this.price = Objects.requireNonNull(price);
    this.imageId = imageId;
    this.flavors = List.copyOf(flavors);
    this.components = List.copyOf(components);
  }

  /** 仅在应用服务完成分类、图片和套餐明细校验后发布销售状态. */
  public void changeSale(boolean value) {
    onSale = value;
  }

  /** 校验套餐或后续购物车选取的口味，必选项不能缺失，未知规格不能注入. */
  public void validateSelections(Map<String, String> selected) {
    if (kind != ProductKind.DISH) {
      throw new CatalogException(CatalogException.Reason.INVALID_INPUT, "套餐明细必须是菜品");
    }
    for (String key : selected.keySet()) {
      if (flavors.stream().noneMatch(group -> group.name().equals(key))) {
        throw new CatalogException(CatalogException.Reason.INVALID_INPUT, "存在未知口味组");
      }
    }
    for (var group : flavors) {
      String value = selected.get(group.name());
      if (value == null && group.required() || value != null && !group.options().contains(value)) {
        throw new CatalogException(CatalogException.Reason.INVALID_INPUT, "口味选择不完整或不合法");
      }
    }
  }

  /** 变更必须基于最新业务版本. */
  public void requireVersion(long expected) {
    if (version != expected) {
      throw new CatalogException(CatalogException.Reason.VERSION_CONFLICT, "商品已被修改");
    }
  }

  /** 返回标识. */
  public UUID id() {
    return id;
  }

  /** 返回种类. */
  public ProductKind kind() {
    return kind;
  }

  /** 返回分类标识. */
  public UUID categoryId() {
    return categoryId;
  }

  /** 返回名称. */
  public String name() {
    return name;
  }

  /** 返回描述. */
  public String description() {
    return description;
  }

  /** 返回精确价格. */
  public Money price() {
    return price;
  }

  /** 返回可选图片资源标识. */
  public UUID imageId() {
    return imageId;
  }

  /** 返回不可变口味集合. */
  public List<FlavorGroup> flavors() {
    return flavors;
  }

  /** 返回不可变套餐明细. */
  public List<MealComponent> components() {
    return components;
  }

  /** 返回销售状态. */
  public boolean onSale() {
    return onSale;
  }

  /** 返回持久化版本. */
  public long version() {
    return version;
  }

  /** 返回创建时刻. */
  public Instant createdAt() {
    return createdAt;
  }
}
