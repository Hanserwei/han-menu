package com.hanserwei.hanmenu.ordering.web.admin;

import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import com.hanserwei.hanmenu.ordering.application.OrderLifecycleService;
import com.hanserwei.hanmenu.ordering.application.OrderViews;
import com.hanserwei.hanmenu.ordering.domain.Order;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 员工履约资源从当前员工身份授权，所有动作都携带最新订单版本. */
@RestController
@RequestMapping("/api/v1/management/orders")
@Tag(name = "员工订单履约")
class OrderManagementController {
  private final OrderLifecycleService lifecycle;

  OrderManagementController(OrderLifecycleService lifecycle) {
    this.lifecycle = lifecycle;
  }

  /** 按可选状态分页读取订单，响应摘要不包含地址. */
  @GetMapping
  @Operation(summary = "分页查询待处理或历史订单")
  OrderViews.History history(
      @AuthenticationPrincipal StaffIdentity identity,
      @RequestParam(required = false) Order.Status status,
      @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return lifecycle.history(identity, status, page, size);
  }

  /** 读取收货与商品快照供当前授权员工履约. */
  @GetMapping("/{id}")
  @Operation(summary = "读取履约订单详情")
  OrderViews.Detail detail(@AuthenticationPrincipal StaffIdentity identity, @PathVariable UUID id) {
    return lifecycle.detail(identity, id);
  }

  /** 接收已付款订单. */
  @PostMapping("/{id}/acceptance")
  @Operation(summary = "接收已付款订单")
  OrderViews.Detail accept(
      @AuthenticationPrincipal StaffIdentity identity,
      @PathVariable UUID id,
      @Valid @RequestBody Version body) {
    return lifecycle.act(identity, id, body.version(), OrderLifecycleService.Action.ACCEPT);
  }

  /** 拒绝已付款订单并申请全额退款. */
  @PostMapping("/{id}/rejection")
  @Operation(summary = "拒绝已付款订单并申请全额退款")
  OrderViews.Detail reject(
      @AuthenticationPrincipal StaffIdentity identity,
      @PathVariable UUID id,
      @Valid @RequestBody Version body) {
    return lifecycle.act(identity, id, body.version(), OrderLifecycleService.Action.REJECT);
  }

  /** 取消接单前或配送前订单并申请全额退款. */
  @PostMapping("/{id}/cancellation")
  @Operation(summary = "取消接单前或配送前订单并申请全额退款")
  OrderViews.Detail cancel(
      @AuthenticationPrincipal StaffIdentity identity,
      @PathVariable UUID id,
      @Valid @RequestBody Version body) {
    return lifecycle.act(identity, id, body.version(), OrderLifecycleService.Action.CANCEL);
  }

  /** 开始配送已接单订单. */
  @PostMapping("/{id}/delivery")
  @Operation(summary = "开始配送已接单订单")
  OrderViews.Detail deliver(
      @AuthenticationPrincipal StaffIdentity identity,
      @PathVariable UUID id,
      @Valid @RequestBody Version body) {
    return lifecycle.act(identity, id, body.version(), OrderLifecycleService.Action.DELIVER);
  }

  /** 完成配送中的订单. */
  @PostMapping("/{id}/completion")
  @Operation(summary = "完成配送中的订单")
  OrderViews.Detail complete(
      @AuthenticationPrincipal StaffIdentity identity,
      @PathVariable UUID id,
      @Valid @RequestBody Version body) {
    return lifecycle.act(identity, id, body.version(), OrderLifecycleService.Action.COMPLETE);
  }

  /** 版本保护并发接单、取消及配送竞争. */
  record Version(@NotNull @Min(0) Long version) {}
}
