package com.hanserwei.hanmenu.shop.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 单店营业条件和并发版本的纯领域测试. */
class ShopTest {
  @Test
  void openingRequiresCompleteContactDetails() {
    var shop = new Shop("门店", "", "", false, 0);
    assertThatThrownBy(() -> shop.changeOpen(true)).isInstanceOf(ShopException.class);
    shop.revise("门店", "13800138000", "测试地址");
    shop.changeOpen(true);
    assertThat(shop.open()).isTrue();
    assertThatThrownBy(() -> shop.revise("门店", "", "")).isInstanceOf(ShopException.class);
    assertThatThrownBy(() -> shop.requireVersion(1)).isInstanceOf(ShopException.class);
    shop.changeOpen(false);
    assertThat(shop.open()).isFalse();
  }
}
