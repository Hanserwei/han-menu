package com.hanserwei.hanmenu.identity.domain;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** 仅在设置密码时短暂使用的值对象，拒绝超出 BCrypt 有效输入范围的密码. */
public record NewPassword(String value) {
  /** 要求 12 至 64 个字符且 UTF-8 编码不超过 72 字节，避免密码被静默截断. */
  public NewPassword {
    Objects.requireNonNull(value, "密码不能为空");
    if (value.length() < 12
        || value.length() > 64
        || value.isBlank()
        || value.getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new IdentityException(
          IdentityException.Reason.INVALID_INPUT, "密码须为 12 至 64 个字符，且 UTF-8 编码不超过 72 字节");
    }
  }

  /** 密码对象不允许通过默认字符串表示泄露原文. */
  @Override
  public String toString() {
    return "NewPassword[已隐藏]";
  }
}
