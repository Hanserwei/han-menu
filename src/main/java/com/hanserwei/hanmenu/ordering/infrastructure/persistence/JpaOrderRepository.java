package com.hanserwei.hanmenu.ordering.infrastructure.persistence;

import com.hanserwei.hanmenu.ordering.domain.Order;
import com.hanserwei.hanmenu.ordering.domain.OrderException;
import com.hanserwei.hanmenu.ordering.domain.OrderPage;
import com.hanserwei.hanmenu.ordering.domain.OrderRepository;
import com.hanserwei.hanmenu.ordering.domain.OrderSearch;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 订单及明细与购物车结算共用事务；唯一键及 ORM 版本作为并发兜底. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JpaOrderRepository implements OrderRepository {
  private final OrderRecords records;

  JpaOrderRepository(OrderRecords records) {
    this.records = records;
  }

  @Override
  public void add(Order order) {
    records.saveAndFlush(OrderEntity.from(order));
  }

  @Override
  public Optional<Order> findOwned(UUID customerId, UUID id) {
    return records.findByCustomerIdAndId(customerId, id).map(OrderEntity::domain);
  }

  @Override
  public Optional<Order> findSubmitted(UUID customerId, String key) {
    return records.findByCustomerIdAndIdempotencyKey(customerId, key).map(OrderEntity::domain);
  }

  @Override
  public void update(Order order) {
    var entity = records.findByCustomerIdAndId(order.customerId(), order.id()).orElseThrow();
    if (entity.version != order.version()) {
      throw new ObjectOptimisticLockingFailureException(OrderEntity.class, order.id());
    }
    entity.applyState(order);
    records.flush();
  }

  @Override
  public OrderPage history(UUID customerId, int page, int size) {
    var result =
        records.findByCustomerId(
            customerId,
            PageRequest.of(
                page, size, Sort.by("createdAt").descending().and(Sort.by("id").descending())));
    return new OrderPage(
        result.getContent().stream().map(OrderSummaryValue::domain).toList(),
        result.getTotalElements());
  }

  @Override
  public Order lock(UUID id) {
    return records
        .findLockedById(id)
        .orElseThrow(() -> new OrderException(OrderException.Reason.NOT_FOUND, "订单不存在"))
        .domain();
  }

  @Override
  public Optional<Order> find(UUID id) {
    return records.findById(id).map(OrderEntity::domain);
  }

  @Override
  public List<UUID> expired(java.time.Instant before, int limit) {
    return records
        .findByStatusAndCreatedAtLessThanEqualOrderByCreatedAtAscIdAsc(
            Order.Status.UNPAID, before, PageRequest.of(0, limit))
        .stream()
        .map(value -> value.id)
        .toList();
  }

  @Override
  public OrderPage management(OrderSearch search) {
    Specification<OrderEntity> filters =
        (root, query, builder) -> {
          var predicates = new ArrayList<Predicate>();
          if (search.status() != null) {
            predicates.add(builder.equal(root.get("status"), search.status()));
          }
          if (search.orderId() != null) {
            predicates.add(builder.equal(root.get("id"), search.orderId()));
          }
          if (search.customerId() != null) {
            predicates.add(builder.equal(root.get("customerId"), search.customerId()));
          }
          if (search.phone() != null) {
            predicates.add(builder.equal(root.get("address").get("phone"), search.phone()));
          }
          if (search.from() != null) {
            predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), search.from()));
          }
          if (search.to() != null) {
            predicates.add(builder.lessThan(root.get("createdAt"), search.to()));
          }
          return builder.and(predicates.toArray(Predicate[]::new));
        };
    var result =
        records.findBy(
            filters,
            query ->
                query
                    .as(OrderSummaryValue.class)
                    .page(
                        PageRequest.of(
                            search.page(),
                            search.size(),
                            Sort.by("createdAt").descending().and(Sort.by("id").descending()))));
    return new OrderPage(
        result.getContent().stream().map(OrderSummaryValue::domain).toList(),
        result.getTotalElements());
  }

  @Override
  public List<Order> factsAfter(UUID cursor, int limit) {
    var page = PageRequest.of(0, limit);
    var values =
        cursor == null
            ? records.findAllByOrderByIdAsc(page)
            : records.findByIdGreaterThanOrderByIdAsc(cursor, page);
    if (!values.isEmpty()) {
      records.findByIdIn(values.stream().map(value -> value.id).toList());
    }
    return values.stream().map(OrderEntity::domain).toList();
  }
}
