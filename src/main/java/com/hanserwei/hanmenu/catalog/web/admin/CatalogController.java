package com.hanserwei.hanmenu.catalog.web.admin;

import com.hanserwei.hanmenu.catalog.api.CatalogViews;
import com.hanserwei.hanmenu.catalog.application.CatalogAdministration;
import com.hanserwei.hanmenu.catalog.application.CatalogImages;
import com.hanserwei.hanmenu.catalog.domain.FlavorGroup;
import com.hanserwei.hanmenu.catalog.domain.MealComponent;
import com.hanserwei.hanmenu.catalog.domain.Money;
import com.hanserwei.hanmenu.catalog.domain.ProductKind;
import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 管理端目录资源；写操作均有管理员权限及版本保护，DTO 在边界显式转换. */
@RestController
@RequestMapping("/api/v1/catalog")
@Tag(name = "商品目录管理")
class CatalogController {
  private final CatalogAdministration catalog;
  private final CatalogImages images;

  CatalogController(CatalogAdministration catalog, CatalogImages images) {
    this.catalog = catalog;
    this.images = images;
  }

  @GetMapping("/categories")
  @Operation(summary = "查询全部分类")
  List<CatalogViews.CategoryView> categories(@AuthenticationPrincipal StaffIdentity actor) {
    return catalog.categories(actor);
  }

  @PostMapping("/categories")
  @Operation(summary = "创建分类")
  @ApiResponse(responseCode = "201", description = "分类创建成功")
  ResponseEntity<CatalogViews.CategoryView> createCategory(
      @AuthenticationPrincipal StaffIdentity actor, @Valid @RequestBody CategoryCreate body) {
    var result = catalog.createCategory(actor, body.kind(), body.name(), body.sortOrder());
    return ResponseEntity.created(URI.create("/api/v1/catalog/categories/" + result.id()))
        .body(result);
  }

  @GetMapping("/categories/{id}")
  @Operation(summary = "读取分类")
  CatalogViews.CategoryView category(
      @AuthenticationPrincipal StaffIdentity actor, @PathVariable UUID id) {
    return catalog.getCategory(actor, id);
  }

  @PutMapping("/categories/{id}")
  @Operation(summary = "更新分类和启停用状态")
  CatalogViews.CategoryView updateCategory(
      @AuthenticationPrincipal StaffIdentity actor,
      @PathVariable UUID id,
      @Valid @RequestBody CategoryUpdate body) {
    return catalog.updateCategory(
        actor, id, body.name(), body.sortOrder(), body.enabled(), body.version());
  }

  @DeleteMapping("/categories/{id}")
  @Operation(summary = "删除无引用分类")
  @ApiResponse(responseCode = "204", description = "分类删除成功")
  ResponseEntity<Void> deleteCategory(
      @AuthenticationPrincipal StaffIdentity actor,
      @PathVariable UUID id,
      @RequestParam @Min(0) long version) {
    catalog.deleteCategory(actor, id, version);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/products")
  @Operation(summary = "后台商品分页，包括下架商品")
  CatalogViews.ProductPage products(
      @AuthenticationPrincipal StaffIdentity actor,
      @RequestParam(required = false) ProductKind kind,
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return catalog.products(actor, kind, categoryId, page, size);
  }

  @GetMapping("/products/{id}")
  @Operation(summary = "读取商品详情")
  CatalogViews.ProductView product(
      @AuthenticationPrincipal StaffIdentity actor, @PathVariable UUID id) {
    return catalog.get(actor, id);
  }

  @PostMapping("/products")
  @Operation(summary = "创建下架菜品或套餐")
  @ApiResponse(responseCode = "201", description = "商品创建成功")
  ResponseEntity<CatalogViews.ProductView> create(
      @AuthenticationPrincipal StaffIdentity actor, @Valid @RequestBody ProductCreate body) {
    var result =
        catalog.createProduct(
            actor,
            body.kind(),
            body.details().categoryId(),
            body.details().name(),
            body.details().description(),
            new Money(body.details().price()),
            body.details().imageId(),
            flavors(body.details()),
            components(body.details()));
    return ResponseEntity.created(URI.create("/api/v1/catalog/products/" + result.id()))
        .body(result);
  }

  @PutMapping("/products/{id}")
  @Operation(summary = "编辑下架商品")
  CatalogViews.ProductView update(
      @AuthenticationPrincipal StaffIdentity actor,
      @PathVariable UUID id,
      @Valid @RequestBody ProductUpdate body) {
    return catalog.updateProduct(
        actor,
        id,
        body.details().categoryId(),
        body.details().name(),
        body.details().description(),
        new Money(body.details().price()),
        body.details().imageId(),
        flavors(body.details()),
        components(body.details()),
        body.version());
  }

  @PatchMapping("/products/{id}/status")
  @Operation(summary = "商品上下架，校验分类与套餐组成")
  CatalogViews.ProductView status(
      @AuthenticationPrincipal StaffIdentity actor,
      @PathVariable UUID id,
      @Valid @RequestBody SaleChange body) {
    return catalog.changeSale(actor, id, body.status() == SaleStatus.ON_SALE, body.version());
  }

  @DeleteMapping("/products/{id}")
  @Operation(summary = "删除下架且无引用的商品")
  @ApiResponse(responseCode = "204", description = "商品删除成功")
  ResponseEntity<Void> delete(
      @AuthenticationPrincipal StaffIdentity actor,
      @PathVariable UUID id,
      @RequestParam @Min(0) long version) {
    catalog.deleteProduct(actor, id, version);
    return ResponseEntity.noContent().build();
  }

  @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "上传 PNG 或 JPEG 商品图片", description = "最多 5 MiB，校验实际格式和像素尺寸")
  @ApiResponse(responseCode = "201", description = "图片创建成功")
  ResponseEntity<CatalogImages.ImageView> upload(
      @AuthenticationPrincipal StaffIdentity actor, @RequestPart("file") MultipartFile file)
      throws IOException {
    var result = images.upload(actor, file.getBytes());
    return ResponseEntity.created(URI.create("/api/v1/catalog/images/" + result.id())).body(result);
  }

  @GetMapping("/images/{id}")
  @Operation(summary = "获取私有图片的五分钟签名读取地址")
  CatalogImages.ImageLink image(
      @AuthenticationPrincipal StaffIdentity actor, @PathVariable UUID id) {
    return images.link(id, actor, false);
  }

  private List<FlavorGroup> flavors(ProductDetails body) {
    return body.flavors().stream()
        .map(value -> new FlavorGroup(value.name(), value.options(), value.required()))
        .toList();
  }

  private List<MealComponent> components(ProductDetails body) {
    return body.components().stream()
        .map(value -> new MealComponent(value.dishId(), value.quantity(), value.selections()))
        .toList();
  }

  /** 分类创建输入，创建后不能改变种类. */
  record CategoryCreate(
      @NotNull ProductKind kind,
      @NotBlank @Size(max = 50) String name,
      @Min(0) @Max(10000) int sortOrder) {}

  /** 分类修改必须带版本，启停用是显式请求. */
  record CategoryUpdate(
      @NotBlank @Size(max = 50) String name,
      @Min(0) @Max(10000) int sortOrder,
      @NotNull Boolean enabled,
      @NotNull @Min(0) Long version) {}

  /** 商品创建将种类与可修改资料分开. */
  record ProductCreate(@NotNull ProductKind kind, @NotNull @Valid ProductDetails details) {}

  /** 商品资料整体替换必须提供版本，不接受改变种类或直接赋值状态. */
  record ProductUpdate(@NotNull @Valid ProductDetails details, @NotNull @Min(0) Long version) {}

  /** 显式使用空数组表示没有口味或没有套餐明细，避免 null 被解释为局部更新. */
  record ProductDetails(
      @NotNull UUID categoryId,
      @NotBlank @Size(max = 100) String name,
      @NotNull @Size(max = 1000) String description,
      @NotNull @DecimalMin("0.01") @Digits(integer = 6, fraction = 2) BigDecimal price,
      UUID imageId,
      @NotNull @Size(max = 10) List<@NotNull @Valid FlavorInput> flavors,
      @NotNull @Size(max = 50) List<@NotNull @Valid ComponentInput> components) {}

  /** 口味组表示一个单选维度. */
  record FlavorInput(
      @NotBlank @Size(max = 30) String name,
      @NotNull @Size(min = 1, max = 20) List<@NotBlank @Size(max = 30) String> options,
      boolean required) {}

  /** 套餐中固定的菜品数量与口味选择. */
  record ComponentInput(
      @NotNull UUID dishId,
      @Min(1) @Max(99) int quantity,
      @NotNull @Size(max = 10)
          Map<@NotBlank @Size(max = 30) String, @NotBlank @Size(max = 30) String> selections) {}

  /** 上下架命令必须携带版本. */
  record SaleChange(@NotNull SaleStatus status, @NotNull @Min(0) Long version) {}

  /** 商品销售状态的公开枚举. */
  enum SaleStatus {
    ON_SALE,
    OFF_SALE
  }
}
