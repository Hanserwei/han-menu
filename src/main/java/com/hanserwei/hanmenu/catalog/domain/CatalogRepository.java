package com.hanserwei.hanmenu.catalog.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 目录仓储端口，跨聚合变更在目录修订锁下串行，保证分类与套餐引用的一致性. */
public interface CatalogRepository {
  /** 锁定并递增目录修订号，与本次写操作同事务提交或回滚. */
  long advanceRevision();

  /** 返回当前已提交修订号，用于缓存代际隔离. */
  long revision();

  /** 在结算事务内固定目录代际，不递增修订号或改变缓存代际. */
  void lockForCheckout();

  /** 查询分类. */
  Optional<Category> category(UUID id);

  /** 有界查询分类，目录最多容纳 100 个分类. */
  List<Category> categories(boolean enabledOnly);

  /** 保存新分类或按版本更新现有分类. */
  void saveCategory(Category category, boolean created);

  /** 删除无引用分类. */
  void deleteCategory(UUID id);

  /** 检查分类引用，可选择只检查在售商品. */
  boolean categoryUsed(UUID id, boolean onSaleOnly);

  /** 查询商品聚合. */
  Optional<MenuProduct> product(UUID id);

  /** 创建或按版本保存商品. */
  void saveProduct(MenuProduct product, boolean created);

  /** 删除下架且无套餐引用的商品. */
  void deleteProduct(UUID id);

  /** 检查套餐引用，可选择只检查在售套餐. */
  boolean dishUsed(UUID id, boolean onSaleOnly);

  /** 按条件分页，公开查询仅返回在售且分类启用的商品. */
  ProductPage products(ProductKind kind, UUID categoryId, boolean publicOnly, int page, int size);

  /** 保存受限图片元数据. */
  void addImage(CatalogImage image);

  /** 查询图片元数据. */
  Optional<CatalogImage> image(UUID id);

  /** 判断图片是否被可售商品引用，控制公开临时下载入口. */
  boolean imagePublished(UUID id);

  /** 统计指定类型和可售状态的商品数量，供工作台公开摘要使用. */
  long countProducts(ProductKind kind, boolean onSale);
}
