package com.hanserwei.hanmenu.ordering.web.app;

import com.hanserwei.hanmenu.customer.api.CustomerIdentity;
import com.hanserwei.hanmenu.ordering.application.OrderPaymentService;
import com.hanserwei.hanmenu.payment.api.PaymentOperations;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 顾客创建订单支付意图，金额只来自已保存的订单快照. */
@RestController
class OrderPaymentController {
  private final OrderPaymentService payments;

  OrderPaymentController(OrderPaymentService payments) {
    this.payments = payments;
  }

  /** 新建返回 201，同键重试返回 200 及同一支付标识，签名参数不作为付款成功依据. */
  @PostMapping("/api/v1/orders/{id}/payments")
  @Operation(summary = "幂等创建订单支付宝沙箱 App 支付意图")
  ResponseEntity<PaymentOperations.AppPayment> create(
      @AuthenticationPrincipal CustomerIdentity identity,
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Create body) {
    var result = payments.create(identity, id, key, body.version());
    return ResponseEntity.status(result.replayed() ? 200 : 201)
        .location(URI.create("/api/v1/payments/" + result.payment().id()))
        .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
        .body(result.payment());
  }

  /** 请求不接受金额、收款方或支付结果. */
  record Create(@NotNull @Min(0) Long version) {}
}
