package com.hanserwei.hanmenu.catalog.web.app;

import com.hanserwei.hanmenu.catalog.api.CatalogViews;
import com.hanserwei.hanmenu.catalog.application.CatalogImages;
import com.hanserwei.hanmenu.catalog.application.PublicCatalog;
import com.hanserwei.hanmenu.catalog.domain.ProductKind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Flutter App 可匿名浏览在售菜单，创建订单等写能力留给后续已认证顾客用例. */
@RestController
@RequestMapping("/api/v1/menu")
@SecurityRequirements
@Tag(name = "公开菜单")
class MenuController {
  private final PublicCatalog catalog;
  private final CatalogImages images;

  MenuController(PublicCatalog catalog, CatalogImages images) {
    this.catalog = catalog;
    this.images = images;
  }

  @GetMapping("/categories")
  @Operation(summary = "读取启用的分类")
  List<CatalogViews.CategoryView> categories() {
    return catalog.categories();
  }

  @GetMapping("/products")
  @Operation(summary = "读取在售商品分页")
  CatalogViews.ProductPage products(
      @RequestParam(required = false) ProductKind kind,
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return catalog.products(kind, categoryId, page, size);
  }

  @GetMapping("/products/{id}")
  @Operation(summary = "读取可售商品详情")
  CatalogViews.ProductView product(@PathVariable UUID id) {
    return catalog.availableProduct(id);
  }

  @GetMapping("/images/{id}")
  @Operation(summary = "获取在售商品图片的临时读取地址")
  CatalogImages.ImageLink image(@PathVariable UUID id) {
    return images.link(id, null, true);
  }
}
