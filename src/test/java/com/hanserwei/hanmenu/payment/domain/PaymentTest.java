package com.hanserwei.hanmenu.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 验证渠道事实单调性、未知结果、领取恢复和全额退款条件. */
class PaymentTest {
  private static final Instant NOW = Instant.parse("2026-09-19T00:00:00Z");
  private static final BigDecimal AMOUNT = new BigDecimal("18.50");

  @Test
  void signedAppParametersCanOnlyBeIssuedWithinOriginalDeadline() {
    var value = payment();
    value.requirePayable(NOW);
    assertThatThrownBy(() -> value.requirePayable(NOW.plusSeconds(900)))
        .isInstanceOf(PaymentException.class);
    value.requestClose(NOW);
    assertThatThrownBy(() -> value.requirePayable(NOW)).isInstanceOf(PaymentException.class);
    assertThat(value.status()).isEqualTo(Payment.Status.PENDING);
  }

  @Test
  void missingChannelTradeBeforeDeadlineDoesNotProveClosure() {
    var value = payment();
    value.requestClose(NOW);
    var missing = new PaymentGateway.TradeResult(PaymentGateway.State.NOT_FOUND, null, null, null);
    assertThat(value.observe(missing, NOW.plusSeconds(901))).isFalse();
    assertThat(value.status()).isEqualTo(Payment.Status.PENDING);
    assertThat(value.observe(missing, NOW.plusSeconds(1021))).isTrue();
    assertThat(value.status()).isEqualTo(Payment.Status.CLOSED);
    assertThat(value.nextAttemptAt()).isNotNull();
  }

  @Test
  void successCannotRegressAndLateSuccessAfterClosureStillRecordsRealMoney() {
    var value = payment();
    value.observe(result(PaymentGateway.State.CLOSED), NOW);
    assertThat(value.observe(result(PaymentGateway.State.SUCCEEDED), NOW)).isTrue();
    assertThat(value.observe(result(PaymentGateway.State.SUCCEEDED), NOW)).isFalse();
    assertThat(value.observe(result(PaymentGateway.State.CLOSED), NOW)).isFalse();
    assertThat(value.status()).isEqualTo(Payment.Status.SUCCEEDED);
    assertThat(value.nextAttemptAt()).isNull();
    assertThatThrownBy(
            () ->
                value.observe(
                    new PaymentGateway.TradeResult(
                        PaymentGateway.State.SUCCEEDED, "other", AMOUNT, NOW),
                    NOW))
        .isInstanceOf(PaymentException.class);
  }

  @Test
  void mismatchedAmountsAndMissingPaymentTimeCannotBeAccepted() {
    var value = payment();
    assertThatThrownBy(
            () ->
                value.observe(
                    new PaymentGateway.TradeResult(
                        PaymentGateway.State.SUCCEEDED, "trade", new BigDecimal("0.01"), NOW),
                    NOW))
        .isInstanceOf(PaymentException.class);
    assertThatThrownBy(
            () ->
                value.observe(
                    new PaymentGateway.TradeResult(
                        PaymentGateway.State.SUCCEEDED, "trade", AMOUNT, null),
                    NOW))
        .isInstanceOf(PaymentException.class);
    assertThat(value.status()).isEqualTo(Payment.Status.PENDING);
  }

  @Test
  void taskClaimExpiresAndFailuresNeverCreateSuccessfulPaymentsOrRefunds() {
    var value = payment();
    assertThat(value.claim(NOW)).isTrue();
    assertThat(value.claim(NOW.plusSeconds(59))).isFalse();
    assertThat(value.claim(NOW.plusSeconds(60))).isTrue();
    value.retry(NOW.plusSeconds(61));
    assertThat(value.status()).isEqualTo(Payment.Status.PENDING);
    assertThat(value.lastFailure()).isEqualTo("CHANNEL_UNAVAILABLE");
    assertThatThrownBy(() -> Refund.create(value, NOW)).isInstanceOf(PaymentException.class);
    value.observe(result(PaymentGateway.State.SUCCEEDED), NOW);
    var refund = Refund.create(value, NOW);
    assertThat(refund.amount()).isEqualByComparingTo(AMOUNT);
    refund.retry(NOW);
    assertThat(refund.status()).isEqualTo(Refund.Status.PENDING);
    assertThat(refund.confirm(NOW)).isTrue();
    assertThat(refund.confirm(NOW)).isFalse();
    refund.retry(NOW);
    assertThat(refund.status()).isEqualTo(Refund.Status.SUCCEEDED);
    assertThat(refund.nextAttemptAt()).isNull();
  }

  private Payment payment() {
    return Payment.create(
        UUID.randomUUID(),
        UUID.randomUUID(),
        AMOUNT,
        "key",
        "fingerprint",
        NOW,
        NOW.plusSeconds(900));
  }

  private PaymentGateway.TradeResult result(PaymentGateway.State state) {
    return new PaymentGateway.TradeResult(
        state, "trade", AMOUNT, state == PaymentGateway.State.SUCCEEDED ? NOW : null);
  }
}
