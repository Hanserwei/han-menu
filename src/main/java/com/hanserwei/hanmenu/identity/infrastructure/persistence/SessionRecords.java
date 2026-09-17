package com.hanserwei.hanmenu.identity.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** 认证查询用实体图一次加载账号，避免懒加载越过事务边界或形成额外查询. */
interface SessionRecords
    extends JpaRepository<SessionEntity, String>, JpaSpecificationExecutor<SessionEntity> {
  @EntityGraph(attributePaths = "employee")
  Optional<SessionEntity> findByTokenHashAndExpiresAtAfterAndEmployeeEnabledTrue(
      String tokenHash, Instant now);
}
