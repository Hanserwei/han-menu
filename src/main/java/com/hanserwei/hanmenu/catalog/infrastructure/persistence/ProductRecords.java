package com.hanserwei.hanmenu.catalog.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** 商品分页先查询主实体，再用实体图抓取当前页明细，避免集合分页截断和 N+1. */
interface ProductRecords
    extends JpaRepository<ProductEntity, UUID>, JpaSpecificationExecutor<ProductEntity> {
  boolean existsByCategoryId(UUID id);

  boolean existsByImageIdAndOnSaleTrue(UUID id);

  boolean existsByCategoryIdAndOnSaleTrue(UUID id);

  boolean existsByComponentsDishId(UUID id);

  boolean existsByComponentsDishIdAndOnSaleTrue(UUID id);

  @EntityGraph(attributePaths = "components")
  List<ProductEntity> findByIdIn(List<UUID> ids);

  long countByKindAndOnSale(com.hanserwei.hanmenu.catalog.domain.ProductKind kind, boolean onSale);
}
