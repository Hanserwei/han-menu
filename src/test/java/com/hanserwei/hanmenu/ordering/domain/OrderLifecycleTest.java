package com.hanserwei.hanmenu.ordering.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 验证付款、取消、退款及履约状态机，迟到结果不能复活已取消订单. */
class OrderLifecycleTest {
  private static final Instant NOW = Instant.parse("2026-09-19T00:00:00Z");

  @Test
  void fulfillmentRequiresPaymentAndEveryPrecedingBusinessStep() {
    var order = order();
    assertThatThrownBy(() -> order.accept(0, NOW)).isInstanceOf(OrderException.class);
    UUID payment = UUID.randomUUID();
    order.attachPayment(payment, 0, NOW);
    order.paymentSucceeded(payment, order.total(), NOW, NOW);
    assertThatThrownBy(() -> order.complete(0, NOW)).isInstanceOf(OrderException.class);
    order.accept(0, NOW);
    assertThatThrownBy(() -> order.cancel(0, NOW)).isInstanceOf(OrderException.class);
    order.deliver(0, NOW);
    assertThatThrownBy(
            () -> order.requestCancellation(0, Order.CancelReason.MERCHANT_CANCELLED, NOW))
        .isInstanceOf(OrderException.class);
    order.complete(0, NOW);
    order.paymentClosed(payment, order.total(), NOW);
    order.paymentSucceeded(payment, order.total(), NOW, NOW);
    assertThat(order.status()).isEqualTo(Order.Status.COMPLETED);
  }

  @Test
  void cancellationWaitsForRealChannelClosureAndLatePaidFactStartsCompensation() {
    var order = order();
    UUID payment = UUID.randomUUID();
    order.attachPayment(payment, 0, NOW);
    order.cancel(0, NOW);
    assertThat(order.status()).isEqualTo(Order.Status.CANCELLING);
    order.paymentClosed(payment, order.total(), NOW);
    assertThat(order.status()).isEqualTo(Order.Status.CANCELLED);
    assertThat(order.paymentSucceeded(payment, order.total(), NOW, NOW)).isTrue();
    assertThat(order.status()).isEqualTo(Order.Status.CANCELLED);
    assertThat(order.lifecycle().refundStatus()).isEqualTo(Order.RefundStatus.PENDING);
    order.refundSucceeded(payment, UUID.randomUUID(), order.total(), NOW);
    assertThat(order.status()).isEqualTo(Order.Status.CANCELLED);
    assertThat(order.lifecycle().refundStatus()).isEqualTo(Order.RefundStatus.SUCCEEDED);
  }

  @Test
  void refundResultCanArriveBeforePaymentEventWithoutReopeningTheOrder() {
    var order = order();
    UUID payment = UUID.randomUUID();
    UUID refund = UUID.randomUUID();
    order.attachPayment(payment, 0, NOW);
    order.cancel(0, NOW);
    order.refundSucceeded(payment, refund, order.total(), NOW);
    assertThat(order.paymentSucceeded(payment, order.total(), NOW, NOW)).isFalse();
    order.refundSucceeded(payment, refund, order.total(), NOW);
    assertThat(order.status()).isEqualTo(Order.Status.CANCELLED);
  }

  @Test
  void timeoutRejectsNewPaymentsAndPaidRejectionRequiresRefundConfirmation() {
    var expired = order();
    assertThatThrownBy(() -> expired.attachPayment(UUID.randomUUID(), 0, NOW.plusSeconds(900)))
        .isInstanceOf(OrderException.class);
    expired.requestCancellation(0, Order.CancelReason.TIMEOUT, NOW.plusSeconds(900));
    assertThat(expired.status()).isEqualTo(Order.Status.CANCELLED);
    var paid = order();
    UUID payment = UUID.randomUUID();
    paid.attachPayment(payment, 0, NOW);
    paid.paymentSucceeded(payment, paid.total(), NOW, NOW);
    paid.requestCancellation(0, Order.CancelReason.MERCHANT_REJECTED, NOW);
    assertThat(paid.status()).isEqualTo(Order.Status.REFUNDING);
    assertThat(paid.cancelledAt()).isNull();
    paid.refundSucceeded(payment, UUID.randomUUID(), paid.total(), NOW);
    assertThat(paid.status()).isEqualTo(Order.Status.CANCELLED);
  }

  @Test
  void reminderRequiresPaidActiveStateVersionAndCooldown() {
    var order = order();
    assertThatThrownBy(() -> order.remind(0, NOW)).isInstanceOf(OrderException.class);
    UUID payment = UUID.randomUUID();
    order.attachPayment(payment, 0, NOW);
    order.paymentSucceeded(payment, order.total(), NOW, NOW);
    order.remind(0, NOW);
    assertThatThrownBy(() -> order.remind(1, NOW.plusSeconds(60)))
        .isInstanceOf(OrderException.class);
    assertThatThrownBy(() -> order.remind(0, NOW.plusSeconds(59)))
        .isInstanceOf(OrderException.class);
    order.remind(0, NOW.plusSeconds(60));
    assertThat(order.reminderCount()).isEqualTo(2);
    order.cancel(0, NOW.plusSeconds(61));
    assertThatThrownBy(() -> order.remind(0, NOW.plusSeconds(121)))
        .isInstanceOf(OrderException.class);
  }

  private Order order() {
    return Order.submit(
        UUID.randomUUID(),
        "key",
        "digest",
        new AddressSnapshot(
            UUID.randomUUID(), 0, "测试顾客", "+8613800138000", "浙江省", "杭州市", "西湖区", "测试地址"),
        List.of(
            new OrderLine(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "DISH",
                "面条",
                new BigDecimal("18.50"),
                1,
                Map.of(),
                List.of())),
        NOW);
  }
}
