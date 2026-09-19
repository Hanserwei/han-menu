package com.hanserwei.hanmenu.ordering.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** 归属和幂等查询由 Spring Data 派生，详情一次加载快照明细. */
interface OrderRecords extends JpaRepository<OrderEntity, UUID> {
  @EntityGraph(attributePaths = "lines")
  Optional<OrderEntity> findByCustomerIdAndId(UUID customerId, UUID id);

  @EntityGraph(attributePaths = "lines")
  Optional<OrderEntity> findByCustomerIdAndIdempotencyKey(UUID customerId, String key);

  Page<OrderSummaryValue> findByCustomerId(UUID customerId, Pageable pageable);
}
