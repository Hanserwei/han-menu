package com.hanserwei.hanmenu.identity.domain;

import java.util.Locale;
import java.util.Objects;

/** 账号资料值对象，仅收集身份管理需要的信息，构造时统一规范化并校验约束. */
public record EmployeeProfile(String username, String displayName, String phone) {
  /** 校验账号名、显示名称及可选联系电话，不承担独立人事档案的职责. */
  public EmployeeProfile {
    username = normalizeUsername(username);
    displayName = Objects.requireNonNull(displayName, "显示名称不能为空").strip();
    phone = phone == null ? "" : phone.strip();
    if (displayName.isBlank()
        || displayName.length() > 50
        || !phone.matches("(?:\\+?[1-9][0-9]{6,14})?")) {
      throw new IdentityException(IdentityException.Reason.INVALID_INPUT, "账号资料格式不正确");
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

  /** 调试输出不暴露个人资料. */
  @Override
  public String toString() {
    return "EmployeeProfile[资料已隐藏]";
  }
}
