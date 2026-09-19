package com.hanserwei.hanmenu.catalog.application;

import com.hanserwei.hanmenu.catalog.api.CatalogSummary;
import com.hanserwei.hanmenu.catalog.domain.CatalogRepository;
import com.hanserwei.hanmenu.catalog.domain.ProductKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 目录摘要在模块内部以派生计数查询实现，跨模块只传递不可变数量. */
@Service
@Transactional(readOnly = true)
class CatalogSummaryService implements CatalogSummary {
  private final CatalogRepository repository;

  CatalogSummaryService(CatalogRepository repository) {
    this.repository = repository;
  }

  @Override
  public Counts counts() {
    return new Counts(
        repository.countProducts(ProductKind.DISH, true),
        repository.countProducts(ProductKind.DISH, false),
        repository.countProducts(ProductKind.SET_MEAL, true),
        repository.countProducts(ProductKind.SET_MEAL, false));
  }
}
