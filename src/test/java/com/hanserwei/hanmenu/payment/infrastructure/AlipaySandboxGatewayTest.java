package com.hanserwei.hanmenu.payment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alipay.api.internal.util.AlipaySignature;
import com.hanserwei.hanmenu.payment.domain.PaymentException;
import com.hanserwei.hanmenu.payment.domain.PaymentGateway;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 使用真实 RSA2 签名测试官方 SDK 适配，不访问真实支付宝或使用沙箱私钥. */
class AlipaySandboxGatewayTest {
  private static KeyPair merchant;
  private static KeyPair alipay;
  private static AlipaySettings settings;

  @BeforeAll
  static void keys() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    merchant = generator.generateKeyPair();
    alipay = generator.generateKeyPair();
    settings =
        new AlipaySettings(
            "9021000000000001",
            "2088000000000001",
            encoded(merchant.getPrivate().getEncoded()),
            encoded(alipay.getPublic().getEncoded()),
            "https://openapi-sandbox.dl.alipaydev.com/gateway.do",
            "https://example.com/api/v1/payment-notifications/alipay");
  }

  @Test
  void appParametersContainServerAmountFixedExpiryAndVerifiableMerchantSignature()
      throws Exception {
    var gateway = new AlipaySandboxGateway(settings);
    var id = UUID.randomUUID();
    String signed =
        gateway.appParameters(
            new PaymentGateway.TradeRequest(
                id, new BigDecimal("18.50"), Instant.parse("2026-09-19T00:15:00Z")));
    var parameters = new TreeMap<String, String>();
    for (String part : signed.split("&")) {
      String[] pair = part.split("=", 2);
      parameters.put(
          URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
          URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
    }
    String signature = parameters.remove("sign");
    var verifier = Signature.getInstance("SHA256withRSA");
    verifier.initVerify(merchant.getPublic());
    verifier.update(
        parameters.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining("&"))
            .getBytes(StandardCharsets.UTF_8));
    assertThat(verifier.verify(Base64.getDecoder().decode(signature))).isTrue();
    assertThat(parameters)
        .containsEntry("method", "alipay.trade.app.pay")
        .containsEntry("sign_type", "RSA2");
    assertThat(parameters.get("biz_content"))
        .contains(id.toString(), "18.50", "2026-09-19 08:15:00", "QUICK_MSECURITY_PAY");
    assertThat(settings.toString()).doesNotContain(settings.privateKey(), settings.publicKey());
  }

  @Test
  void notificationsRequireRealSignatureApplicationSellerAndUntamperedFields() throws Exception {
    var gateway = new AlipaySandboxGateway(settings);
    var parameters = notice();
    var verified = gateway.verify(parameters);
    assertThat(verified.result().state()).isEqualTo(PaymentGateway.State.SUCCEEDED);
    assertThat(verified.result().amount()).isEqualByComparingTo("18.50");
    assertThat(verified.result().paidAt()).isEqualTo(Instant.parse("2026-09-19T00:00:00Z"));
    for (String field :
        new String[] {
          "app_id", "seller_id", "sign", "total_amount", "out_trade_no", "trade_no", "trade_status"
        }) {
      var tampered = new HashMap<>(parameters);
      tampered.put(field, "forged");
      assertThatThrownBy(() -> gateway.verify(tampered)).isInstanceOf(PaymentException.class);
    }
    var missing = new HashMap<>(parameters);
    missing.remove("gmt_payment");
    assertThatThrownBy(() -> gateway.verify(missing)).isInstanceOf(PaymentException.class);
  }

  @Test
  void productionGatewayAndMissingCredentialsCannotBeUsed() {
    var production =
        new AlipaySettings(
            settings.appId(),
            settings.sellerId(),
            settings.privateKey(),
            settings.publicKey(),
            "https://openapi.alipay.com/gateway.do",
            settings.notifyUrl());
    assertThatThrownBy(() -> new AlipaySandboxGateway(production).requireConfigured())
        .isInstanceOf(PaymentException.class);
    assertThatThrownBy(
            () ->
                new AlipaySandboxGateway(new AlipaySettings("", "", "", "", "", ""))
                    .requireConfigured())
        .isInstanceOf(PaymentException.class);
  }

  @Test
  void successfulEmptyRefundQueryAllowsFirstRefundButConfirmedResultMustMatch() throws Exception {
    var gateway = new AlipaySandboxGateway(settings);
    var client = org.mockito.Mockito.mock(com.alipay.api.AlipayClient.class);
    org.springframework.test.util.ReflectionTestUtils.setField(gateway, "client", client);
    var response = new com.alipay.api.response.AlipayTradeFastpayRefundQueryResponse();
    response.setCode("10000");
    org.mockito.Mockito.when(
            client.execute(
                org.mockito.ArgumentMatchers.any(
                    com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest.class)))
        .thenReturn(response);
    var request =
        new PaymentGateway.RefundRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "2026091922001400000000000001",
            new BigDecimal("18.50"));
    assertThat(gateway.refundSucceeded(request)).isFalse();
    response.setRefundStatus("REFUND_SUCCESS");
    assertThatThrownBy(() -> gateway.refundSucceeded(request)).isInstanceOf(PaymentException.class);
    response.setOutTradeNo(request.paymentId().toString());
    response.setTradeNo(request.tradeNo());
    response.setOutRequestNo(request.refundId().toString());
    response.setRefundAmount("18.50");
    response.setTotalAmount("18.50");
    assertThat(gateway.refundSucceeded(request)).isTrue();
    response.setRefundAmount("0.01");
    assertThatThrownBy(() -> gateway.refundSucceeded(request)).isInstanceOf(PaymentException.class);
  }

  private Map<String, String> notice() throws Exception {
    var parameters = new HashMap<String, String>();
    parameters.put("app_id", settings.appId());
    parameters.put("seller_id", settings.sellerId());
    parameters.put("out_trade_no", UUID.randomUUID().toString());
    parameters.put("trade_no", "2026091922001400000000000001");
    parameters.put("total_amount", "18.50");
    parameters.put("trade_status", "TRADE_SUCCESS");
    parameters.put("gmt_payment", "2026-09-19 08:00:00");
    parameters.put(
        "sign",
        AlipaySignature.sign(
            parameters, encoded(alipay.getPrivate().getEncoded()), "UTF-8", "RSA2"));
    parameters.put("sign_type", "RSA2");
    return parameters;
  }

  private static String encoded(byte[] bytes) {
    return Base64.getEncoder().encodeToString(bytes);
  }
}
