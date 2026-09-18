package com.hanserwei.hanmenu.catalog.application;

import com.hanserwei.hanmenu.catalog.api.CatalogQuery;
import com.hanserwei.hanmenu.catalog.api.CatalogViews;
import com.hanserwei.hanmenu.catalog.domain.CatalogCache;
import com.hanserwei.hanmenu.catalog.domain.CatalogException;
import com.hanserwei.hanmenu.catalog.domain.CatalogRepository;
import com.hanserwei.hanmenu.catalog.domain.ProductKind;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** 面向 App 的公开查询，以数据库修订号隔离缓存，不把缓存当作下单时的价格依据. */
@Service
@Transactional(readOnly = true)
public class PublicCatalog implements CatalogQuery {
  private static final Logger LOGGER = LoggerFactory.getLogger(PublicCatalog.class);
  private final CatalogRepository repository;
  private final CatalogCache cache;
  private final JsonMapper json;

  /** 注入查询端口，缓存异常由适配器降级，损坏快照在此回源. */
  public PublicCatalog(CatalogRepository repository, CatalogCache cache, JsonMapper json) {
    this.repository = repository;
    this.cache = cache;
    this.json = json;
  }

  /** 公开分类只包含启用项. */
  public List<CatalogViews.CategoryView> categories() {
    return repository.categories(true).stream().map(CatalogMapper::category).toList();
  }

  /** 公开列表缓存不包含签名 URL；事务回滚不会发布新的缓存代际. */
  public CatalogViews.ProductPage products(ProductKind kind, UUID categoryId, int page, int size) {
    CatalogAdministration.validatePage(page, size);
    String key = repository.revision() + ":" + kind + ":" + categoryId + ":" + page + ":" + size;
    var cached = cache.get(key);
    if (cached.isPresent()) {
      try {
        var view = json.readValue(cached.orElseThrow(), CatalogViews.ProductPage.class);
        if (view != null) {
          return view;
        }
      } catch (RuntimeException ignored) {
        // 损坏缓存仅作为未命中，不改变数据库事实。
        LOGGER.debug("catalog_cache_invalid");
      }
    }
    var result = repository.products(kind, categoryId, true, page, size);
    var view =
        new CatalogViews.ProductPage(
            result.items().stream().map(CatalogMapper::product).toList(),
            page,
            size,
            result.totalElements(),
            Math.ceilDiv(result.totalElements(), size));
    cache.put(key, json.writeValueAsString(view));
    return view;
  }

  /** 单项可售检查直接读取数据库，未来订单和购物车调用该契约仍须在各自用例校验版本. */
  @Override
  public CatalogViews.ProductView availableProduct(UUID id) {
    var value =
        repository
            .product(id)
            .filter(product -> product.onSale())
            .orElseThrow(
                () -> new CatalogException(CatalogException.Reason.NOT_FOUND, "商品不存在或已下架"));
    if (!repository.category(value.categoryId()).orElseThrow().enabled()) {
      throw new CatalogException(CatalogException.Reason.NOT_FOUND, "商品分类已停用");
    }
    return CatalogMapper.product(value);
  }

  /** 商品删除、下架或分类关闭时返回空，仅用于只读展示的降级处理. */
  @Override
  public Optional<CatalogViews.ProductView> findAvailableProduct(UUID id) {
    return repository
        .product(id)
        .filter(value -> value.onSale())
        .filter(
            value ->
                repository
                    .category(value.categoryId())
                    .map(category -> category.enabled())
                    .orElse(false))
        .map(CatalogMapper::product);
  }
}
