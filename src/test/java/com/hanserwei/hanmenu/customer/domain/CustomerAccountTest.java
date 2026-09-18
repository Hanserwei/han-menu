package com.hanserwei.hanmenu.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 顾客身份与地址约束可以脱离 Spring 独立验证. */
class CustomerAccountTest {
  @Test
  void normalizationAndProfileChangesPreserveIdentity() {
    var account =
        CustomerAccount.create(UUID.randomUUID(), " 13800138000 ", " 顾客 ", "hash", Instant.now());
    assertThat(account.phone()).isEqualTo("+13800138000");
    assertThat(CustomerAccount.normalizePhone("+13800138000")).isEqualTo(account.phone());
    account.reviseProfile("新昵称", Instant.now());
    assertThat(account.displayName()).isEqualTo("新昵称");
    assertThat(account.toString()).doesNotContain(account.phone(), account.displayName(), "hash");
    assertThatThrownBy(() -> account.requireVersion(1)).isInstanceOf(CustomerException.class);
  }

  @Test
  void disabledAndPasswordChangedAccountsCannotUseOldSessions() {
    var account =
        CustomerAccount.create(UUID.randomUUID(), "13800138000", "顾客", "hash", Instant.now());
    account.changeEnabled(false, Instant.now());
    assertThatThrownBy(() -> account.requireActive(0)).isInstanceOf(CustomerException.class);
    account.changeEnabled(true, Instant.now());
    assertThatThrownBy(() -> account.requireActive(0)).isInstanceOf(CustomerException.class);
    account.requireActive(2);
    account.changePassword("new-hash", Instant.now());
    assertThatThrownBy(() -> account.requireActive(2)).isInstanceOf(CustomerException.class);
    account.requireActive(3);
  }

  @Test
  void passwordsAndAddressesValidateInputsAndHidePrivateData() {
    assertThatThrownBy(() -> new CustomerPassword("short")).isInstanceOf(CustomerException.class);
    assertThatThrownBy(() -> new CustomerPassword("中".repeat(25)))
        .isInstanceOf(CustomerException.class);
    assertThat(new CustomerPassword("valid-password-2026").toString())
        .doesNotContain("valid-password-2026");
    var now = Instant.now();
    var address =
        new DeliveryAddress(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "家",
            "收货人",
            "13800138000",
            "浙江",
            "杭州",
            "西湖",
            "详细地址",
            false,
            0,
            now,
            now);
    address.makeDefault(now);
    assertThat(address.defaultAddress()).isTrue();
    address.clearDefault(now);
    assertThat(address.defaultAddress()).isFalse();
    assertThat(address.toString()).doesNotContain("收货人", "详细地址", "13800138000");
    assertThatThrownBy(() -> address.requireVersion(1)).isInstanceOf(CustomerException.class);
  }
}
