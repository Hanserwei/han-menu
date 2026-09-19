package com.hanserwei.hanmenu.ordering.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** 归属和幂等查询由 Spring Data 派生，详情一次加载快照明细. */
interface OrderRecords
    extends JpaRepository<OrderEntity, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<OrderEntity> {
  @EntityGraph(attributePaths = "lines")
  Optional<OrderEntity> findByCustomerIdAndId(UUID customerId, UUID id);

  @EntityGraph(attributePaths = "lines")
  Optional<OrderEntity> findByCustomerIdAndIdempotencyKey(UUID customerId, String key);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  Optional<OrderEntity> findLockedById(UUID id);

  java.util.List<OrderEntity> findByStatusAndCreatedAtLessThanEqualOrderByCreatedAtAscIdAsc(
      com.hanserwei.hanmenu.ordering.domain.Order.Status status,
      java.time.Instant before,
      Pageable pageable);

  java.util.List<OrderEntity> findAllByOrderByIdAsc(Pageable pageable);

  java.util.List<OrderEntity> findByIdGreaterThanOrderByIdAsc(UUID cursor, Pageable pageable);

  @EntityGraph(attributePaths = "lines")
  java.util.List<OrderEntity> findByIdIn(java.util.Collection<UUID> ids);

  Page<OrderSummaryValue> findByCustomerId(UUID customerId, Pageable pageable);
}
