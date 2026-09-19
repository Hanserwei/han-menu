package com.hanserwei.hanmenu.catalog.application;

import com.hanserwei.hanmenu.catalog.api.CatalogCheckout;
import com.hanserwei.hanmenu.catalog.api.CatalogViews;
import com.hanserwei.hanmenu.catalog.domain.CatalogException;
import com.hanserwei.hanmenu.catalog.domain.CatalogRepository;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 结算期间持有目录修订锁，所有商品与组成来自同一可售代际. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
class CatalogCheckoutService implements CatalogCheckout {
  private final CatalogRepository repository;
  private final PublicCatalog catalog;

  CatalogCheckoutService(CatalogRepository repository, PublicCatalog catalog) {
    this.repository = repository;
    this.catalog = catalog;
  }

  @Override
  public QuotedProduct quote(UUID productId, Map<String, String> selections) {
    repository.lockForCheckout();
    var product = catalog.availableProduct(productId);
    requireSelections(product, selections);
    var components = new ArrayList<ComponentSnapshot>();
    for (var component : product.components()) {
      var dish = catalog.availableProduct(component.dishId());
      repository.product(dish.id()).orElseThrow().validateSelections(component.selections());
      components.add(
          new ComponentSnapshot(
              dish.id(), dish.name(), component.quantity(), component.selections()));
    }
    return new QuotedProduct(product, components);
  }

  private void requireSelections(CatalogViews.ProductView product, Map<String, String> selections) {
    if (selections == null
        || selections.size() > 10
        || (!product.kind().equals("DISH") && !selections.isEmpty())) {
      throw new CatalogException(CatalogException.Reason.INVALID_INPUT, "商品规格已失效");
    }
    if (product.kind().equals("DISH")) {
      // 复用商品聚合的必选口味规则，结算不能形成另一套不一致的规格校验。
      repository.product(product.id()).orElseThrow().validateSelections(selections);
    }
  }
}
