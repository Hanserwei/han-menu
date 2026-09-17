package com.hanserwei.hanmenu.identity.api;

import java.util.UUID;

/** 认证后的员工身份快照，供接口适配器或其他模块使用，不携带令牌和敏感个人资料. */
public record StaffIdentity(
    UUID employeeId, String username, String displayName, String role, long securityVersion) {
  /** 调试信息仅保留内部编号和角色，不输出姓名及用户名. */
  @Override
  public String toString() {
    return "StaffIdentity[employeeId=" + employeeId + ", role=" + role + "]";
  }
}
