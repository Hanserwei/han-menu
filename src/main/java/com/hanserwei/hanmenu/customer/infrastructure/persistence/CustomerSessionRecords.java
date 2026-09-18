package com.hanserwei.hanmenu.customer.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** 顾客认证使用实体图避免请求序列化期间的懒加载. */
interface CustomerSessionRecords
    extends JpaRepository<CustomerSessionEntity, String>,
        JpaSpecificationExecutor<CustomerSessionEntity> {
  @EntityGraph(attributePaths = "customer")
  Optional<CustomerSessionEntity> findByTokenHashAndExpiresAtAfterAndCustomerEnabledTrue(
      String tokenHash, Instant now);
}
