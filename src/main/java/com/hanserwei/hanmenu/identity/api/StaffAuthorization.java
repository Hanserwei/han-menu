package com.hanserwei.hanmenu.identity.api;

/** 公开的员工权限检查能力，调用模块不需要读取身份模块实体或仓储. */
public interface StaffAuthorization {
  /** 再次检查账号当前状态与安全版本，并要求管理员权限. */
  void requireAdministrator(StaffIdentity identity);
}
