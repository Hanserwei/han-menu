package com.hanserwei.hanmenu.cart.web.app;

import com.hanserwei.hanmenu.cart.application.CartService;
import com.hanserwei.hanmenu.customer.api.CustomerIdentity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 当前顾客的购物车资源，客户端不能传入顾客标识或商品价格. */
@RestController
@RequestMapping("/api/v1/cart")
@Tag(name = "顾客购物车")
class CartController {
  private final CartService carts;

  CartController(CartService carts) {
    this.carts = carts;
  }

  /** 读取实时展示状态，包括保留的失效条目. */
  @GetMapping
  @Operation(summary = "读取本人购物车")
  CartService.CartView get(@AuthenticationPrincipal CustomerIdentity identity) {
    return carts.get(identity);
  }

  /** 增加数量而非覆盖，同商品同规格合并；版本使重试不会重复增加. */
  @PostMapping("/items")
  @Operation(summary = "添加商品并合并同规格条目")
  CartService.CartView add(
      @AuthenticationPrincipal CustomerIdentity identity, @Valid @RequestBody AddItem body) {
    return carts.add(
        identity, body.productId(), body.quantity(), body.selections(), body.version());
  }

  /** 数量替换为目标值，零数量须使用删除接口表达. */
  @PatchMapping("/items/{id}")
  @Operation(summary = "修改条目数量")
  CartService.CartView quantity(
      @AuthenticationPrincipal CustomerIdentity identity,
      @PathVariable UUID id,
      @Valid @RequestBody QuantityChange body) {
    return carts.changeQuantity(identity, id, body.quantity(), body.version());
  }

  /** 移除条目并返回新版本. */
  @DeleteMapping("/items/{id}")
  @Operation(summary = "删除条目")
  CartService.CartView remove(
      @AuthenticationPrincipal CustomerIdentity identity,
      @PathVariable UUID id,
      @RequestParam @Min(0) long version) {
    return carts.remove(identity, id, version);
  }

  /** 清空后仍保留版本，不重置为零. */
  @DeleteMapping("/items")
  @Operation(summary = "清空本人购物车")
  CartService.CartView clear(
      @AuthenticationPrincipal CustomerIdentity identity, @RequestParam @Min(0) long version) {
    return carts.clear(identity, version);
  }

  /** 所有变更都显式提交读取到的购物车版本. */
  record AddItem(
      @NotNull UUID productId,
      @Min(1) @Max(99) int quantity,
      @NotNull @Size(max = 10)
          Map<@NotBlank @Size(max = 30) String, @NotBlank @Size(max = 30) String> selections,
      @NotNull @Min(0) Long version) {}

  /** 条目数量变更不携带顾客或商品标识. */
  record QuantityChange(@Min(1) @Max(99) int quantity, @NotNull @Min(0) Long version) {}
}
