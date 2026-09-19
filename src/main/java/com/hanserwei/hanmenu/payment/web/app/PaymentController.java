package com.hanserwei.hanmenu.payment.web.app;

import com.hanserwei.hanmenu.customer.api.CustomerIdentity;
import com.hanserwei.hanmenu.payment.application.PaymentReconciliation;
import com.hanserwei.hanmenu.payment.application.PaymentTransactions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 顾客只能查询本人支付和退款，不接受前端成功状态或任意退款金额. */
@RestController
@Tag(name = "顾客支付与退款")
class PaymentController {
  private final PaymentTransactions transactions;
  private final PaymentReconciliation reconciliation;

  PaymentController(PaymentTransactions transactions, PaymentReconciliation reconciliation) {
    this.transactions = transactions;
    this.reconciliation = reconciliation;
  }

  /** 读取数据库中已经确认的支付状态. */
  @GetMapping("/api/v1/payments/{id}")
  @Operation(summary = "查询本人支付状态")
  PaymentTransactions.PaymentView get(
      @AuthenticationPrincipal CustomerIdentity identity, @PathVariable UUID id) {
    return transactions.view(identity, id);
  }

  /** App 恢复时主动触发有界查单，HTTP 层不持有数据库事务. */
  @PostMapping("/api/v1/payments/{id}/refresh")
  @Operation(summary = "主动核对本人支付状态")
  PaymentTransactions.PaymentView refresh(
      @AuthenticationPrincipal CustomerIdentity identity,
      @PathVariable UUID id,
      @Valid @RequestBody Refresh body) {
    transactions.authorizeRefresh(identity, id, body.version());
    reconciliation.payment(id);
    return transactions.view(identity, id);
  }

  /** 查询全额退款的处理或确认状态. */
  @GetMapping("/api/v1/refunds/{id}")
  @Operation(summary = "查询本人退款状态")
  PaymentTransactions.RefundView refund(
      @AuthenticationPrincipal CustomerIdentity identity, @PathVariable UUID id) {
    return transactions.refundView(identity, id);
  }

  /** 刷新要求明确版本，避免陈旧页面无意覆盖处理进度. */
  record Refresh(@NotNull @Min(0) Long version) {}
}
