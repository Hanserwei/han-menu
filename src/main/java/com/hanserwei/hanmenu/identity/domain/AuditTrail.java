package com.hanserwei.hanmenu.identity.domain;

import java.util.UUID;

/** 记录结构化的最小安全审计，不接收任意文本、密码、令牌或个人资料. */
public interface AuditTrail {
  /** 记录可信枚举动作和内部标识；成功操作与业务事务共同提交. */
  void record(Action action, UUID actorId, UUID subjectId, boolean success);

  /** 可记录的安全事件，限制日志内容来源以防注入和凭证泄露. */
  enum Action {
    BOOTSTRAP,
    LOGIN,
    LOGOUT,
    CREATE_EMPLOYEE,
    UPDATE_EMPLOYEE,
    CHANGE_STATUS,
    CHANGE_CUSTOMER_STATUS,
    CHANGE_PASSWORD,
    AUTHORIZATION_DENIED,
    LOGIN_LIMITED
  }
}
