package com.hanserwei.hanmenu.ordering.infrastructure.persistence;

import com.hanserwei.hanmenu.ordering.domain.Order;
import com.hanserwei.hanmenu.ordering.domain.OrderPage;
import com.hanserwei.hanmenu.ordering.domain.OrderRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
}
