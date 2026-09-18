package com.hanserwei.hanmenu.customer.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** 顾客会话仓储，原始令牌不进入数据库. */
public interface CustomerSessionRepository {
  /** 保存令牌摘要和安全版本. */
  void add(String tokenHash, UUID customerId, long securityVersion, Instant expiresAt);

  /** 查询未过期且安全版本匹配的会话. */
  Optional<CustomerAccount> findActive(String tokenHash, Instant now);

  /** 撤销单个会话. */
  void revoke(String tokenHash);

  /** 删除过期会话. */
  void deleteExpired(Instant now);
}
