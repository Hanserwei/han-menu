package com.hanserwei.hanmenu.identity.domain;

import java.time.Instant;
import java.util.Optional;

/** 保存令牌摘要和撤销版本的会话端口，原始令牌不能进入数据库. */
public interface SessionRepository {
  /** 保存新会话；安全版本不匹配的会话后续不能通过认证. */
  void add(String tokenHash, long employeeId, long securityVersion, Instant expiresAt);

  /** 只返回未过期、未停用且安全版本匹配的账号. */
  Optional<EmployeeAccount> findActive(String tokenHash, Instant now);

  /** 撤销当前令牌，重复调用不会复活或创建会话. */
  void revoke(String tokenHash);

  /** 清理到期会话，避免长期运行无限累积. */
  void deleteExpired(Instant now);
}
