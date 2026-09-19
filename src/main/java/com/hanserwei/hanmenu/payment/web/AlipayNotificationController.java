package com.hanserwei.hanmenu.payment.web;

import com.hanserwei.hanmenu.payment.application.PaymentTransactions;
import com.hanserwei.hanmenu.payment.domain.PaymentGateway;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import java.util.HashMap;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 支付宝表单通知使用渠道签名认证，成功响应仅在支付事实及事件登记提交后发送. */
@RestController
class AlipayNotificationController {
  private final PaymentGateway gateway;
  private final PaymentTransactions transactions;

  AlipayNotificationController(PaymentGateway gateway, PaymentTransactions transactions) {
    this.gateway = gateway;
    this.transactions = transactions;
  }

  /** 拒绝重复参数、防止验签歧义；任何校验或事务失败都不确认通知成功. */
  @PostMapping(
      value = "/api/v1/payment-notifications/alipay",
      consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
      produces = MediaType.TEXT_PLAIN_VALUE)
  @Operation(summary = "接收并验签支付宝沙箱通知")
  @SecurityRequirements
  ResponseEntity<String> notify(@RequestParam MultiValueMap<String, String> parameters) {
    if (parameters.size() > 64
        || parameters.values().stream().anyMatch(values -> values.size() != 1)) {
      return ResponseEntity.badRequest().body("failure");
    }
    try {
      var values = new HashMap<String, String>();
      parameters.forEach((key, value) -> values.put(key, value.getFirst()));
      var notice = gateway.verify(values);
      transactions.observe(notice.paymentId(), notice.result());
      return ResponseEntity.ok("success");
    } catch (RuntimeException exception) {
      return ResponseEntity.badRequest().body("failure");
    }
  }
}
