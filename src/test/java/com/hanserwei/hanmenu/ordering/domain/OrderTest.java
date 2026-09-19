package com.hanserwei.hanmenu.ordering.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 验证订单聚合的快照隔离、精确金额与单向未支付状态迁移. */
class OrderTest {
  @Test
  void snapshotsAreDeeplyImmutableAndTotalsUseExactDecimalArithmetic() {
    var selections = new HashMap<>(Map.of("辣度", "微辣"));
    var line =
        new OrderLine(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "DISH",
            "面条",
            new BigDecimal("18.50"),
            3,
            selections,
            List.of());
    var lines = new ArrayList<>(List.of(line));
    var order = Order.submit(UUID.randomUUID(), "key", "digest", address(), lines, Instant.now());
    selections.clear();
    lines.clear();
    assertThat(order.total()).isEqualByComparingTo("55.50");
    assertThat(order.lines().getFirst().selections()).containsEntry("辣度", "微辣");
    assertThatThrownBy(() -> order.lines().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> line.selections().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(order.address().toString()).doesNotContain("张先生", "13800138000", "示例路");
  }

  @Test
  void cancellationRequiresCurrentVersionAndCannotReopenOrCancelTwice() {
    var order =
        Order.submit(UUID.randomUUID(), "key", "digest", address(), List.of(line()), Instant.now());
    assertThatThrownBy(() -> order.cancel(1, Instant.now())).isInstanceOf(OrderException.class);
    assertThatThrownBy(() -> order.requireSameRequest("other")).isInstanceOf(OrderException.class);
    order.requireSameRequest("digest");
    order.cancel(0, Instant.now());
    assertThat(order.status()).isEqualTo(Order.Status.CANCELLED);
    assertThat(order.cancelledAt()).isNotNull();
    assertThatThrownBy(() -> order.cancel(0, Instant.now())).isInstanceOf(OrderException.class);
    assertThat(order.total()).isEqualByComparingTo("18.50");
  }

  @Test
  void invalidQuantitiesAmountsAndEmptyOrdersCannotBeConstructed() {
    assertThatThrownBy(
            () ->
                Order.submit(
                    UUID.randomUUID(), "key", "digest", address(), List.of(), Instant.now()))
        .isInstanceOf(OrderException.class);
    assertThatThrownBy(
            () ->
                new OrderLine(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "DISH",
                    "面条",
                    new BigDecimal("0.00"),
                    1,
                    Map.of(),
                    List.of()))
        .isInstanceOf(OrderException.class);
    assertThatThrownBy(
            () ->
                new OrderLine(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "DISH",
                    "面条",
                    new BigDecimal("1.001"),
                    1,
                    Map.of(),
                    List.of()))
        .isInstanceOf(ArithmeticException.class);
    assertThatThrownBy(
            () ->
                new OrderLine(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "DISH",
                    "面条",
                    new BigDecimal("18.50"),
                    100,
                    Map.of(),
                    List.of()))
        .isInstanceOf(OrderException.class);
  }

  private AddressSnapshot address() {
    return new AddressSnapshot(
        UUID.randomUUID(), 0, "张先生", "+8613800138000", "浙江省", "杭州市", "西湖区", "示例路 1 号");
  }

  private OrderLine line() {
    return new OrderLine(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "DISH",
        "面条",
        new BigDecimal("18.50"),
        1,
        Map.of(),
        List.of());
  }
}
