package com.hanserwei.hanmenu.cart.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 验证购物车同规格合并、数量上限、集合不可变和版本行为. */
class ShoppingCartTest {
  @Test
  void sameSelectionMergesWhileDifferentSelectionRemainsSeparate() {
    UUID product = UUID.randomUUID();
    var cart = ShoppingCart.empty(UUID.randomUUID(), Instant.now());
    var first = item(product, Map.of("辣度", "微辣"));
    cart = cart.add(first, 2, Instant.now());
    var next =
        new CartItem(
            UUID.randomUUID(),
            product,
            "DISH",
            "新名称",
            new BigDecimal("20.00"),
            1,
            Map.of("辣度", "微辣"));
    cart = cart.add(next, 1, Instant.now());
    assertThat(cart.items()).hasSize(1);
    assertThat(cart.items().getFirst().id()).isEqualTo(first.id());
    assertThat(cart.items().getFirst().quantity()).isEqualTo(3);
    assertThat(cart.items().getFirst().unitPrice()).isEqualByComparingTo("20.00");
    cart = cart.add(item(product, Map.of("辣度", "不辣")), 1, Instant.now());
    assertThat(cart.items()).hasSize(2);
    assertThatThrownBy(() -> first.selections().put("test", "bad"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void quantityVersionAndOwnershipAreAggregateConstraints() {
    var first = item(UUID.randomUUID(), Map.of());
    var cart = new ShoppingCart(UUID.randomUUID(), List.of(first), 4, Instant.now());
    assertThatThrownBy(() -> cart.requireVersion(3)).isInstanceOf(CartException.class);
    assertThatThrownBy(() -> cart.changeQuantity(first.id(), 0, Instant.now()))
        .isInstanceOf(CartException.class);
    assertThatThrownBy(() -> cart.add(first, 99, Instant.now())).isInstanceOf(CartException.class);
    assertThatThrownBy(() -> cart.remove(UUID.randomUUID(), Instant.now()))
        .isInstanceOf(CartException.class);
    assertThat(cart.clear(Instant.now()).version()).isEqualTo(4);
    assertThat(cart.clear(Instant.now()).items()).isEmpty();
  }

  private CartItem item(UUID product, Map<String, String> selections) {
    return new CartItem(
        UUID.randomUUID(), product, "DISH", "菜品", new BigDecimal("18.50"), 1, selections);
  }

  @Test
  void cartHasBoundedDistinctSelections() {
    var cart = ShoppingCart.empty(UUID.randomUUID(), Instant.now());
    for (int index = 0; index < 50; index++) {
      cart = cart.add(item(UUID.randomUUID(), Map.of()), 1, Instant.now());
    }
    var full = cart;
    assertThatThrownBy(() -> full.add(item(UUID.randomUUID(), Map.of()), 1, Instant.now()))
        .isInstanceOf(CartException.class);
    assertThat(full.add(full.items().getFirst(), 1, Instant.now()).items()).hasSize(50);
  }
}
