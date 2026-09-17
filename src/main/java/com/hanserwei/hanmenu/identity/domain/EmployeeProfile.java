package com.hanserwei.hanmenu.identity.domain;

import java.util.Locale;
import java.util.Objects;

/** 员工档案值对象，在构造时统一规范化并保护字段约束，不包含登录凭证. */
public record EmployeeProfile(
    String username, String name, String phone, String sex, String idNumber) {
  /** 校验用户名和个人资料；电话号码和身份证仅做格式校验，不表示已核验身份. */
  public EmployeeProfile {
    username = normalizeUsername(username);
    name = Objects.requireNonNull(name, "员工姓名不能为空").strip();
    phone = phone == null ? "" : phone.strip();
    sex = sex == null ? "2" : sex;
    idNumber = idNumber == null ? "" : idNumber.strip().toUpperCase(Locale.ROOT);
    if (name.isBlank()
        || name.length() > 50
        || !phone.matches("(?:1[3-9][0-9]{9})?")
        || !sex.matches("[012]")
        || !idNumber.matches("(?:[0-9]{17}[0-9X])?")) {
      throw new IdentityException(IdentityException.Reason.INVALID_INPUT, "员工资料格式不正确");
    }
  }

  /** 用户名不区分大小写，限制为字母开头的 3 至 32 位字母、数字或下划线. */
  public static String normalizeUsername(String value) {
    String result = Objects.requireNonNull(value, "用户名不能为空").strip().toLowerCase(Locale.ROOT);
    if (!result.matches("[a-z][a-z0-9_]{2,31}")) {
      throw new IdentityException(IdentityException.Reason.INVALID_INPUT, "用户名格式不正确");
    }
    return result;
  }

  /** 防止调试输出意外包含员工个人资料. */
  @Override
  public String toString() {
    return "EmployeeProfile[资料已隐藏]";
  }
}
