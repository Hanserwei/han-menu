package com.hanserwei.hanmenu.ordering.application;

import com.hanserwei.hanmenu.ordering.domain.Order;
import com.hanserwei.hanmenu.ordering.domain.OrderRepository;
import com.hanserwei.hanmenu.ordering.events.OrderChanged;
import com.hanserwei.hanmenu.ordering.events.OrderReady;
import com.hanserwei.hanmenu.ordering.events.OrderReminderRaised;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 在业务事务内使用已 flush 的版本登记事件，回滚时不会遗留通知或统计事实. */
@Component
@Transactional(propagation = Propagation.MANDATORY)
class OrderEvents {
  private final OrderRepository orders;
  private final ApplicationEventPublisher events;

  OrderEvents(OrderRepository orders, ApplicationEventPublisher events) {
    this.orders = orders;
    this.events = events;
  }

  void created(UUID id) {
    events.publishEvent(
        new OrderChanged(OrderFactsService.snapshot(orders.find(id).orElseThrow())));
  }

  void changed(Order previous) {
    var current = orders.find(previous.id()).orElseThrow();
    if (current.version() > previous.version()) {
      events.publishEvent(new OrderChanged(OrderFactsService.snapshot(current)));
    }
  }

  void ready(UUID id, Instant paidAt) {
    events.publishEvent(new OrderReady(key("ready:" + id), id, paidAt));
  }

  void reminder(Order order) {
    events.publishEvent(
        new OrderReminderRaised(
            key("reminder:" + order.id() + ":" + order.reminderCount()),
            order.id(),
            order.reminderCount(),
            order.lastRemindedAt()));
  }

  private UUID key(String value) {
    return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
  }
}
