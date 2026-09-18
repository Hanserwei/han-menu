package com.hanserwei.hanmenu.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 商品聚合规则测试，不需要 Spring 或数据库即可验证价格、规格及生命周期. */
class MenuProductTest {
  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "1000000", "1.001"})
  void priceRejectsInvalidValues(String amount) {
    assertThatThrownBy(() -> new Money(new BigDecimal(amount)))
        .isInstanceOf(CatalogException.class);
  }

  @Test
  void publishedProductsMustBeTakenOffSaleBeforeEditing() {
    var product = dish();
    product.changeSale(true);
    assertThatThrownBy(
            () ->
                product.revise(
                    product.categoryId(),
                    "新名称",
                    "",
                    new Money(BigDecimal.TEN),
                    null,
                    List.of(),
                    List.of()))
        .isInstanceOf(CatalogException.class);
    product.changeSale(false);
    product.revise(
        product.categoryId(), "新名称", "", new Money(BigDecimal.TEN), null, List.of(), List.of());
    assertThat(product.name()).isEqualTo("新名称");
    assertThatThrownBy(() -> product.requireVersion(99)).isInstanceOf(CatalogException.class);
  }

  @Test
  void flavorSelectionsMustMatchDeclaredOptions() {
    var dish = dish();
    dish.validateSelections(Map.of("辣度", "微辣"));
    assertThatThrownBy(() -> dish.validateSelections(Map.of()))
        .isInstanceOf(CatalogException.class);
    assertThatThrownBy(() -> dish.validateSelections(Map.of("辣度", "不存在")))
        .isInstanceOf(CatalogException.class);
    assertThatThrownBy(() -> dish.validateSelections(Map.of("未知", "值")))
        .isInstanceOf(CatalogException.class);
    assertThatThrownBy(() -> new FlavorGroup("辣度", List.of("微辣", "微辣"), true))
        .isInstanceOf(CatalogException.class);
  }

  @Test
  void mealCannotBeEmptyOrContainDuplicateDish() {
    UUID id = UUID.randomUUID();
    assertThatThrownBy(() -> meal(List.of())).isInstanceOf(CatalogException.class);
    assertThatThrownBy(
            () ->
                meal(
                    List.of(
                        new MealComponent(id, 1, Map.of()), new MealComponent(id, 2, Map.of()))))
        .isInstanceOf(CatalogException.class);
    var meal = meal(List.of(new MealComponent(id, 1, Map.of())));
    assertThatThrownBy(() -> meal.components().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private MenuProduct dish() {
    return new MenuProduct(
        UUID.randomUUID(),
        ProductKind.DISH,
        UUID.randomUUID(),
        "菜品",
        "",
        new Money(BigDecimal.TEN),
        null,
        List.of(new FlavorGroup("辣度", List.of("微辣", "不辣"), true)),
        List.of(),
        false,
        0,
        Instant.now());
  }

  private MenuProduct meal(List<MealComponent> parts) {
    return new MenuProduct(
        UUID.randomUUID(),
        ProductKind.SET_MEAL,
        UUID.randomUUID(),
        "套餐",
        "",
        new Money(BigDecimal.TEN),
        null,
        List.of(),
        parts,
        false,
        0,
        Instant.now());
  }
}
