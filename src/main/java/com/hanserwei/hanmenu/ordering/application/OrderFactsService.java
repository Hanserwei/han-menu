package com.hanserwei.hanmenu.ordering.application;

import com.hanserwei.hanmenu.ordering.api.OrderFacts;
import com.hanserwei.hanmenu.ordering.domain.Order;
import com.hanserwei.hanmenu.ordering.domain.OrderRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 通过仓储分批读取权威快照，和同事务事件使用同一映射. */
@Service
@Transactional(readOnly = true)
class OrderFactsService implements OrderFacts {
  private final OrderRepository orders;

  OrderFactsService(OrderRepository orders) {
    this.orders = orders;
  }

  @Override
  public List<Snapshot> after(UUID cursor, int limit) {
    if (limit < 1 || limit > 200) {
      throw new IllegalArgumentException("统计快照批次须为 1 至 200");
    }
    return orders.factsAfter(cursor, limit).stream().map(OrderFactsService::snapshot).toList();
  }

  static Snapshot snapshot(Order order) {
    var life = order.lifecycle();
    return new Snapshot(
        order.id(),
        order.customerId(),
        order.version(),
        order.status().name(),
        order.total(),
        order.createdAt(),
        life.paidAt(),
        life.completedAt(),
        order.cancelledAt(),
        life.paymentId(),
        life.refundStatus().name(),
        life.refundId(),
        order.lines().stream()
            .map(
                line ->
                    new Line(
                        line.id(),
                        line.productId(),
                        line.name(),
                        line.kind(),
                        line.quantity(),
                        line.unitPrice()))
            .toList());
  }
}
