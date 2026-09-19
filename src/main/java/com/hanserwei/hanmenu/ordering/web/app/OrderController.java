package com.hanserwei.hanmenu.ordering.web.app;

import com.hanserwei.hanmenu.customer.api.CustomerIdentity;
import com.hanserwei.hanmenu.ordering.application.OrderService;
import com.hanserwei.hanmenu.ordering.application.OrderViews;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 顾客订单资源只从认证主体取得归属，不接受金额、状态或收货内容. */
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "顾客订单")
class OrderController {
  private final OrderService orders;

  OrderController(OrderService orders) {
    this.orders = orders;
  }

  /** 幂等提交返回同一订单；新建为 201，重放为 200，二者都附订单地址. */
  @PostMapping
  @Operation(summary = "幂等提交待付款订单")
  ResponseEntity<OrderViews.Detail> submit(
      @AuthenticationPrincipal CustomerIdentity identity,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Submit body) {
    var result =
        orders.submit(
            identity,
            key,
            new OrderService.SubmitCommand(
                body.addressId(), body.addressVersion(), body.cartVersion(), body.itemIds()));
    return ResponseEntity.status(result.replayed() ? 200 : 201)
        .location(URI.create("/api/v1/orders/" + result.order().id()))
        .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
        .body(result.order());
  }

  /** 返回本人订单历史，分页避免无界读取. */
  @GetMapping
  @Operation(summary = "分页查询本人订单历史")
  OrderViews.History history(
      @AuthenticationPrincipal CustomerIdentity identity,
      @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return orders.history(identity, page, size);
  }

  /** 地址和商品变更不会影响成交详情. */
  @GetMapping("/{id}")
  @Operation(summary = "查询本人订单详情")
  OrderViews.Detail detail(
      @AuthenticationPrincipal CustomerIdentity identity, @PathVariable UUID id) {
    return orders.detail(identity, id);
  }

  /** 仅接受客户端读取到的订单版本. */
  @PostMapping("/{id}/cancellation")
  @Operation(summary = "取消本人待付款订单")
  OrderViews.Detail cancel(
      @AuthenticationPrincipal CustomerIdentity identity,
      @PathVariable UUID id,
      @Valid @RequestBody Cancellation body) {
    return orders.cancel(identity, id, body.version());
  }

  /** 将旧订单商品按当前可售规则加入购物车；任一失效整体拒绝. */
  @PostMapping("/{id}/reorder")
  @Operation(summary = "按当前目录再来一单到购物车")
  OrderService.Reordered reorder(
      @AuthenticationPrincipal CustomerIdentity identity,
      @PathVariable UUID id,
      @Valid @RequestBody Reorder body) {
    return orders.reorder(identity, id, body.version(), body.cartVersion());
  }

  /** 下单只能选择本人地址和购物车条目，价格由服务端决定. */
  record Submit(
      @NotNull UUID addressId,
      @NotNull @Min(0) Long addressVersion,
      @NotNull @Min(0) Long cartVersion,
      @NotNull @Size(min = 1, max = 50) List<@NotNull UUID> itemIds) {}

  /** 取消命令不接受任意目标状态. */
  record Cancellation(@NotNull @Min(0) Long version) {}

  /** 重新加购同时要求订单和购物车版本，防止重复增加数量. */
  record Reorder(@NotNull @Min(0) Long version, @NotNull @Min(0) Long cartVersion) {}
}
