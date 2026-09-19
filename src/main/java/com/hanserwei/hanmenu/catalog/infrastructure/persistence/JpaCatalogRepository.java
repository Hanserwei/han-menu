package com.hanserwei.hanmenu.catalog.infrastructure.persistence;

import com.hanserwei.hanmenu.catalog.domain.CatalogImage;
import com.hanserwei.hanmenu.catalog.domain.CatalogRepository;
import com.hanserwei.hanmenu.catalog.domain.Category;
import com.hanserwei.hanmenu.catalog.domain.MenuProduct;
import com.hanserwei.hanmenu.catalog.domain.ProductKind;
import com.hanserwei.hanmenu.catalog.domain.ProductPage;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** JPA 目录仓储，事务由应用用例统一定义，数据读写不泄露 ORM 实体. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JpaCatalogRepository implements CatalogRepository {
  private final CategoryRecords categories;
  private final ProductRecords products;
  private final ImageRecords images;
  private final RevisionRecords revisions;

  JpaCatalogRepository(
      CategoryRecords categories,
      ProductRecords products,
      ImageRecords images,
      RevisionRecords revisions) {
    this.categories = categories;
    this.products = products;
    this.images = images;
    this.revisions = revisions;
  }

  @Override
  public long advanceRevision() {
    var value = revisions.findLockedById(1).orElseThrow();
    value.revision++;
    revisions.flush();
    return value.revision;
  }

  @Override
  public void lockForCheckout() {
    revisions.findLockedById(1).orElseThrow();
  }

  @Override
  public long revision() {
    return revisions.findById(1).orElseThrow().revision;
  }

  @Override
  public Optional<Category> category(UUID id) {
    return categories.findById(id).map(CategoryEntity::domain);
  }

  @Override
  public List<Category> categories(boolean enabledOnly) {
    return (enabledOnly
            ? categories.findByEnabledTrueOrderBySortOrderAscIdAsc()
            : categories.findAllByOrderBySortOrderAscIdAsc())
        .stream().map(CategoryEntity::domain).toList();
  }

  @Override
  public void saveCategory(Category value, boolean created) {
    var entity = created ? new CategoryEntity() : categories.findById(value.id()).orElseThrow();
    if (!created && entity.version != value.version()) {
      throw new ObjectOptimisticLockingFailureException(CategoryEntity.class, value.id());
    }
    entity.apply(value);
    categories.saveAndFlush(entity);
  }

  @Override
  public void deleteCategory(UUID id) {
    categories.deleteById(id);
    categories.flush();
  }

  @Override
  public boolean categoryUsed(UUID id, boolean onSaleOnly) {
    return onSaleOnly
        ? products.existsByCategoryIdAndOnSaleTrue(id)
        : products.existsByCategoryId(id);
  }

  @Override
  public Optional<MenuProduct> product(UUID id) {
    return products.findById(id).map(ProductEntity::domain);
  }

  @Override
  public void saveProduct(MenuProduct value, boolean created) {
    var entity = created ? new ProductEntity() : products.findById(value.id()).orElseThrow();
    if (!created && entity.version != value.version()) {
      throw new ObjectOptimisticLockingFailureException(ProductEntity.class, value.id());
    }
    entity.apply(value);
    products.saveAndFlush(entity);
  }

  @Override
  public void deleteProduct(UUID id) {
    products.deleteById(id);
    products.flush();
  }

  @Override
  public boolean dishUsed(UUID id, boolean onSaleOnly) {
    return onSaleOnly
        ? products.existsByComponentsDishIdAndOnSaleTrue(id)
        : products.existsByComponentsDishId(id);
  }

  @Override
  public ProductPage products(
      ProductKind kind, UUID categoryId, boolean publicOnly, int page, int size) {
    Specification<ProductEntity> filters =
        (root, query, builder) -> {
          var terms = new ArrayList<Predicate>();
          if (kind != null) {
            terms.add(builder.equal(root.get("kind"), kind));
          }
          if (categoryId != null) {
            terms.add(builder.equal(root.get("categoryId"), categoryId));
          }
          if (publicOnly) {
            terms.add(builder.isTrue(root.get("onSale")));
            var enabled = query.subquery(UUID.class);
            var category = enabled.from(CategoryEntity.class);
            enabled.select(category.get("id")).where(builder.isTrue(category.get("enabled")));
            terms.add(root.get("categoryId").in(enabled));
          }
          return builder.and(terms.toArray(Predicate[]::new));
        };
    var result =
        products.findAll(
            filters,
            PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id"))));
    var ids = result.getContent().stream().map(entity -> entity.id).toList();
    if (!ids.isEmpty()) {
      products.findByIdIn(ids);
    }
    return new ProductPage(
        result.getContent().stream().map(ProductEntity::domain).toList(),
        result.getTotalElements());
  }

  @Override
  public void addImage(CatalogImage image) {
    images.saveAndFlush(ImageEntity.from(image));
  }

  @Override
  public Optional<CatalogImage> image(UUID id) {
    return images.findById(id).map(ImageEntity::domain);
  }

  @Override
  public boolean imagePublished(UUID id) {
    return products.existsByImageIdAndOnSaleTrue(id);
  }
}
