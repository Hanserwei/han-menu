package com.hanserwei.hanmenu.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DishTest {
  @Test
  void publishesDraftOnlyOnce() {
    var dish = Dish.draft(new DishId(UUID.randomUUID()), "  Noodles  ", new Price(BigDecimal.TEN));
    assertThat(dish.name()).isEqualTo("Noodles");
    assertThat(dish.status()).isEqualTo(Dish.Status.DRAFT);
    dish.publish();
    assertThat(dish.status()).isEqualTo(Dish.Status.PUBLISHED);
    assertThatIllegalStateException().isThrownBy(dish::publish);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t\n"})
  void rejectsEmptyNames(String name) {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> Dish.draft(new DishId(UUID.randomUUID()), name, new Price(BigDecimal.ONE)));
  }

  @Test
  void rejectsLongNames() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                Dish.draft(
                    new DishId(UUID.randomUUID()), "x".repeat(101), new Price(BigDecimal.ONE)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "0.001", "1.234", "100000000"})
  void rejectsInvalidPrices(String amount) {
    assertThatIllegalArgumentException().isThrownBy(() -> new Price(new BigDecimal(amount)));
  }

  @Test
  void normalizesExactPrices() {
    assertThat(new Price(new BigDecimal("1.000"))).isEqualTo(new Price(new BigDecimal("1.00")));
  }
}
