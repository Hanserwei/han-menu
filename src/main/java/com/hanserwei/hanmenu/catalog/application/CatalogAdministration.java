package com.hanserwei.hanmenu.catalog.application;

import com.hanserwei.hanmenu.catalog.api.CatalogViews;
import com.hanserwei.hanmenu.catalog.domain.CatalogException;
import com.hanserwei.hanmenu.catalog.domain.CatalogRepository;
import com.hanserwei.hanmenu.catalog.domain.Category;
import com.hanserwei.hanmenu.catalog.domain.FlavorGroup;
import com.hanserwei.hanmenu.catalog.domain.MealComponent;
import com.hanserwei.hanmenu.catalog.domain.MenuProduct;
import com.hanserwei.hanmenu.catalog.domain.Money;
import com.hanserwei.hanmenu.catalog.domain.ProductKind;
import com.hanserwei.hanmenu.identity.api.StaffAuthorization;
import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 目录写用例：在单个数据库事务内串行检查跨聚合约束，并更新缓存代际. */
@Service
@Transactional
public class CatalogAdministration {
  private final CatalogRepository repository;
  private final StaffAuthorization authorization;
  private final Clock clock;

  /** 组合仓储和公开授权契约，避免跨模块引用内部实体. */
  public CatalogAdministration(
      CatalogRepository repository, StaffAuthorization authorization, Clock clock) {
    this.repository = repository;
    this.authorization = authorization;
    this.clock = clock;
  }

  /** 新分类默认启用，后台目录最多容纳一百个分类. */
  public CatalogViews.CategoryView createCategory(
      StaffIdentity actor, ProductKind kind, String name, int sort) {
    begin(actor);
    if (repository.categories(false).size() >= 100) {
      conflict("分类最多支持 100 个");
    }
    var category = new Category(UUID.randomUUID(), kind, name, sort, true, 0);
    repository.saveCategory(category, true);
    return CatalogMapper.category(category);
  }

  /** 修改分类信息，不改变种类；关闭有在售商品的分类会失败. */
  public CatalogViews.CategoryView updateCategory(
      StaffIdentity actor, UUID id, String name, int sort, boolean enabled, long version) {
    begin(actor);
    var value = category(id);
    value.requireVersion(version);
    if (!enabled && repository.categoryUsed(id, true)) {
      conflict("请先下架分类中的商品");
    }
    value.revise(name, sort);
    value.changeEnabled(enabled);
    repository.saveCategory(value, false);
    return CatalogMapper.category(category(id));
  }

  /** 有任何商品引用的分类都不能删除，避免隐式级联破坏商品. */
  public void deleteCategory(StaffIdentity actor, UUID id, long version) {
    begin(actor);
    category(id).requireVersion(version);
    if (repository.categoryUsed(id, false)) {
      conflict("分类仍被商品引用");
    }
    repository.deleteCategory(id);
  }

  /** 读取后台分类包括停用项. */
  @Transactional(readOnly = true)
  public List<CatalogViews.CategoryView> categories(StaffIdentity actor) {
    authorization.requireAdministrator(actor);
    return repository.categories(false).stream().map(CatalogMapper::category).toList();
  }

  /** 读取指定分类，供创建资源后的 Location 查询. */
  @Transactional(readOnly = true)
  public CatalogViews.CategoryView getCategory(StaffIdentity actor, UUID id) {
    authorization.requireAdministrator(actor);
    return CatalogMapper.category(category(id));
  }

  /** 新商品必须明确种类，默认下架；套餐组成在保存前验证引用和口味. */
  public CatalogViews.ProductView createProduct(
      StaffIdentity actor,
      ProductKind kind,
      UUID categoryId,
      String name,
      String description,
      Money price,
      UUID imageId,
      List<FlavorGroup> flavors,
      List<MealComponent> components) {
    begin(actor);
    var value =
        new MenuProduct(
            UUID.randomUUID(),
            kind,
            categoryId,
            name,
            description,
            price,
            imageId,
            flavors,
            components,
            false,
            0,
            clock.instant());
    validate(value, false);
    repository.saveProduct(value, true);
    return CatalogMapper.product(value);
  }

  /** 内容编辑要求下架，且不能修改已被套餐引用菜品的定义以破坏固定口味. */
  public CatalogViews.ProductView updateProduct(
      StaffIdentity actor,
      UUID id,
      UUID categoryId,
      String name,
      String description,
      Money price,
      UUID imageId,
      List<FlavorGroup> flavors,
      List<MealComponent> components,
      long version) {
    begin(actor);
    var value = product(id);
    value.requireVersion(version);
    if (value.kind() == ProductKind.DISH && repository.dishUsed(id, false)) {
      conflict("请先从套餐移除此菜品再编辑");
    }
    value.revise(categoryId, name, description, price, imageId, flavors, components);
    validate(value, false);
    repository.saveProduct(value, false);
    return CatalogMapper.product(product(id));
  }

  /** 上架要求分类有效及全部组成菜品可售；下架被在售套餐引用的菜品必须先下架套餐. */
  public CatalogViews.ProductView changeSale(
      StaffIdentity actor, UUID id, boolean onSale, long version) {
    begin(actor);
    var value = product(id);
    value.requireVersion(version);
    if (onSale) {
      validate(value, true);
    } else if (value.kind() == ProductKind.DISH && repository.dishUsed(id, true)) {
      conflict("请先下架引用此菜品的套餐");
    }
    value.changeSale(onSale);
    repository.saveProduct(value, false);
    return CatalogMapper.product(product(id));
  }

  /** 删除仅允许下架且不被套餐引用的商品，不清理可能被其他商品共享的图片. */
  public void deleteProduct(StaffIdentity actor, UUID id, long version) {
    begin(actor);
    var value = product(id);
    value.requireVersion(version);
    if (value.onSale() || repository.dishUsed(id, false)) {
      conflict("在售或被套餐引用的商品不可删除");
    }
    repository.deleteProduct(id);
  }

  /** 后台单项读取包括下架商品. */
  @Transactional(readOnly = true)
  public CatalogViews.ProductView get(StaffIdentity actor, UUID id) {
    authorization.requireAdministrator(actor);
    return CatalogMapper.product(product(id));
  }

  /** 后台分页不经过公开目录缓存，便于管理员即时核对修改结果. */
  @Transactional(readOnly = true)
  public CatalogViews.ProductPage products(
      StaffIdentity actor, ProductKind kind, UUID categoryId, int page, int size) {
    authorization.requireAdministrator(actor);
    validatePage(page, size);
    var result = repository.products(kind, categoryId, false, page, size);
    return new CatalogViews.ProductPage(
        result.items().stream().map(CatalogMapper::product).toList(),
        page,
        size,
        result.totalElements(),
        Math.ceilDiv(result.totalElements(), size));
  }

  static void validatePage(int page, int size) {
    if (page < 0 || page > 10000 || size < 1 || size > 100) {
      throw new CatalogException(CatalogException.Reason.INVALID_INPUT, "分页范围不合法");
    }
  }

  private void begin(StaffIdentity actor) {
    authorization.requireAdministrator(actor);
    repository.advanceRevision();
  }

  private Category category(UUID id) {
    return repository
        .category(id)
        .orElseThrow(() -> new CatalogException(CatalogException.Reason.NOT_FOUND, "分类不存在"));
  }

  private MenuProduct product(UUID id) {
    return repository
        .product(id)
        .orElseThrow(() -> new CatalogException(CatalogException.Reason.NOT_FOUND, "商品不存在"));
  }

  private void validate(MenuProduct value, boolean publishing) {
    var category = category(value.categoryId());
    if (category.kind() != value.kind()) {
      conflict("分类种类与商品不匹配");
    }
    if (publishing && !category.enabled()) {
      conflict("分类未启用");
    }
    if (value.imageId() != null && repository.image(value.imageId()).isEmpty()) {
      throw new CatalogException(CatalogException.Reason.NOT_FOUND, "图片资源不存在");
    }
    for (var component : value.components()) {
      var dish = product(component.dishId());
      dish.validateSelections(component.selections());
      if (publishing && (!dish.onSale() || !category(dish.categoryId()).enabled())) {
        conflict("套餐中的菜品未上架");
      }
    }
  }

  private void conflict(String message) {
    throw new CatalogException(CatalogException.Reason.CONFLICT, message);
  }
}
