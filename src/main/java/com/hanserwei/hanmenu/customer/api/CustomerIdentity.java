package com.hanserwei.hanmenu.customer.api;

import java.util.UUID;

/** 顾客认证快照，供顾客模块接口和购物车模块传递操作者身份. */
public record CustomerIdentity(
    UUID customerId, String phone, String displayName, long securityVersion) {
  /** 调试输出不包含手机号或其他个人资料. */
  @Override
  public String toString() {
    return "CustomerIdentity[customerId=" + customerId + "]";
  }
}
