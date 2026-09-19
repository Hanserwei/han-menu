package com.hanserwei.hanmenu.identity.api;

import java.util.UUID;

/** 对外提供固定安全事件登记，不接受请求正文、凭证或任意审计文本. */
public interface StaffAudit {
  /** 在调用方业务事务内登记管理员变更顾客状态的事实. */
  void customerStatusChanged(StaffIdentity actor, UUID customerId);
}
