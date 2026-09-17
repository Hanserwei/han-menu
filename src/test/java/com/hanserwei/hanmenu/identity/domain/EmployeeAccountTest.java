package com.hanserwei.hanmenu.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 验证聚合行为和凭证边界，不依赖 Spring 或数据库. */
class EmployeeAccountTest {
  private static final Instant NOW = Instant.parse("2026-09-17T00:00:00Z");

  @Test
  void statusChangesRevokeOldSessionsAndCannotReviveThem() {
    var employee = employee(EmployeeAccount.Role.STAFF);
    employee.changeEnabled(false, NOW.plusSeconds(1));
    assertThat(employee.enabled()).isFalse();
    assertThat(employee.securityVersion()).isEqualTo(1);
    employee.changeEnabled(true, NOW.plusSeconds(2));
    assertThat(employee.securityVersion()).isEqualTo(2);
    assertThatThrownBy(() -> employee.requireActive(0)).isInstanceOf(IdentityException.class);
    employee.requireActive(2);
    employee.changeEnabled(true, NOW.plusSeconds(3));
    assertThat(employee.securityVersion()).isEqualTo(2);
  }

  @Test
  void administratorCannotBeDisabledAndStaffCannotAdminister() {
    assertThatThrownBy(() -> employee(EmployeeAccount.Role.ADMIN).changeEnabled(false, NOW))
        .isInstanceOf(IdentityException.class);
    assertThatThrownBy(() -> employee(EmployeeAccount.Role.STAFF).requireAdministrator())
        .isInstanceOf(IdentityException.class);
  }

  @Test
  void passwordChangeRevokesSessionsAndPreservesRole() {
    var employee = employee(EmployeeAccount.Role.STAFF);
    employee.changePassword("replacement-hash", NOW.plusSeconds(1));
    assertThat(employee.securityVersion()).isEqualTo(1);
    assertThat(employee.passwordHash()).isEqualTo("replacement-hash");
    assertThat(employee.role()).isEqualTo(EmployeeAccount.Role.STAFF);
    assertThatThrownBy(() -> employee.requireVersion(2L)).isInstanceOf(IdentityException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "short", "            "})
  void rejectsWeakPasswords(String password) {
    assertThatThrownBy(() -> new NewPassword(password)).isInstanceOf(IdentityException.class);
  }

  @Test
  void measuresUtf8BytesInsteadOfSilentlyTruncatingPasswords() {
    var accepted = new NewPassword("中".repeat(24));
    assertThat(accepted.toString()).doesNotContain(accepted.value());
    assertThatThrownBy(() -> new NewPassword("中".repeat(25))).isInstanceOf(IdentityException.class);
  }

  @Test
  void normalizesUsernamesAndHidesPersonalInformation() {
    var profile = new EmployeeProfile("  Staff_01  ", " 测试员工 ", "13800138000", "1", "");
    assertThat(profile.username()).isEqualTo("staff_01");
    assertThat(profile.name()).isEqualTo("测试员工");
    assertThat(profile.toString()).doesNotContain(profile.name(), profile.phone());
    assertThatThrownBy(() -> new EmployeeProfile("bad user", "姓名", "", "2", ""))
        .isInstanceOf(IdentityException.class);
  }

  private EmployeeAccount employee(EmployeeAccount.Role role) {
    return EmployeeAccount.create(
        1, new EmployeeProfile("staff", "员工", "", "2", ""), "example-hash", role, NOW);
  }
}
