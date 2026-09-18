package com.hanserwei.hanmenu.catalog.domain;

import java.util.Objects;
import java.util.UUID;

/** 分类聚合，封装排序、名称及启停用状态；跨聚合引用由目录应用服务检查. */
public final class Category {
  private final UUID id;
  private final ProductKind kind;
  private String name;
  private int sortOrder;
  private boolean enabled;
  private final long version;

  /** 重建或创建分类，统一验证持久化状态与业务输入. */
  public Category(
      UUID id, ProductKind kind, String name, int sortOrder, boolean enabled, long version) {
    this.id = Objects.requireNonNull(id);
    this.kind = Objects.requireNonNull(kind);
    revise(name, sortOrder);
    this.enabled = enabled;
    if (version < 0) {
      throw new IllegalArgumentException("版本不可为负");
    }
    this.version = version;
  }

  /** 修改分类描述，不允许隐式改变分类种类. */
  public void revise(String name, int sortOrder) {
    this.name = CatalogText.required(name, 50, "分类名称");
    if (sortOrder < 0 || sortOrder > 10000) {
      throw new CatalogException(CatalogException.Reason.INVALID_INPUT, "分类排序范围为 0 至 10000");
    }
    this.sortOrder = sortOrder;
  }

  /** 分类启停用本身不级联改变商品，应用服务必须先检查在售引用. */
  public void changeEnabled(boolean value) {
    enabled = value;
  }

  /** 拒绝陈旧客户端更新. */
  public void requireVersion(long expected) {
    if (version != expected) {
      throw new CatalogException(CatalogException.Reason.VERSION_CONFLICT, "分类已被修改");
    }
  }

  /** 返回标识. */
  public UUID id() {
    return id;
  }

  /** 返回创建时确定的种类. */
  public ProductKind kind() {
    return kind;
  }

  /** 返回名称. */
  public String name() {
    return name;
  }

  /** 返回排序权重. */
  public int sortOrder() {
    return sortOrder;
  }

  /** 返回是否启用. */
  public boolean enabled() {
    return enabled;
  }

  /** 返回持久化版本. */
  public long version() {
    return version;
  }
}
