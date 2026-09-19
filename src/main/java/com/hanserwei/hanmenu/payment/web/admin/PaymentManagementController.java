package com.hanserwei.hanmenu.payment.web.admin;

import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import com.hanserwei.hanmenu.payment.application.PaymentManagement;
import com.hanserwei.hanmenu.payment.domain.Payment;
import com.hanserwei.hanmenu.payment.domain.Refund;
import com.hanserwei.hanmenu.payment.domain.TransactionSearch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理员资金查询位于员工安全链，不复用顾客身份接口. */
@RestController
@RequestMapping("/api/v1/management")
@Tag(name = "管理员支付与退款查询")
class PaymentManagementController {
  private final PaymentManagement management;

  PaymentManagementController(PaymentManagement management) {
    this.management = management;
  }

  /** 组合检索支付意图，包含尚未付款和已关闭的记录. */
  @GetMapping("/payments")
  @Operation(summary = "分页查询支付流水")
  PaymentManagement.TransactionPageView<PaymentManagement.ManagedPaymentView> payments(
      @AuthenticationPrincipal StaffIdentity actor,
      @RequestParam(required = false) Payment.Status status,
      @RequestParam(required = false) UUID orderId,
      @RequestParam(required = false) UUID customerId,
      @RequestParam(required = false) UUID paymentId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return management.payments(
        actor, status, new TransactionSearch(orderId, customerId, paymentId, from, to, page, size));
  }

  /** 读取持久化支付详情，不隐式触发渠道查单. */
  @GetMapping("/payments/{id}")
  @Operation(summary = "读取支付流水详情")
  PaymentManagement.ManagedPaymentView payment(
      @AuthenticationPrincipal StaffIdentity actor, @PathVariable UUID id) {
    return management.payment(actor, id);
  }

  /** 按原订单、原支付、顾客及创建时刻组合检索退款. */
  @GetMapping("/refunds")
  @Operation(summary = "分页查询退款流水")
  PaymentManagement.TransactionPageView<PaymentManagement.ManagedRefundView> refunds(
      @AuthenticationPrincipal StaffIdentity actor,
      @RequestParam(required = false) Refund.Status status,
      @RequestParam(required = false) UUID orderId,
      @RequestParam(required = false) UUID customerId,
      @RequestParam(required = false) UUID paymentId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return management.refunds(
        actor, status, new TransactionSearch(orderId, customerId, paymentId, from, to, page, size));
  }

  /** 返回后台确认的退款状态，不接受客户端指定成功结果. */
  @GetMapping("/refunds/{id}")
  @Operation(summary = "读取退款流水详情")
  PaymentManagement.ManagedRefundView refund(
      @AuthenticationPrincipal StaffIdentity actor, @PathVariable UUID id) {
    return management.refund(actor, id);
  }
}
