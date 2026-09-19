package com.hanserwei.hanmenu.notification.application;

import com.hanserwei.hanmenu.notification.domain.Notice;
import com.hanserwei.hanmenu.ordering.events.OrderReady;
import com.hanserwei.hanmenu.ordering.events.OrderReminderRaised;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** 可靠消费来单与催单，登记失败时由 Modulith 重投，不在监听事务发送 WebSocket. */
@Component
class OrderNotificationListener {
  private final NotificationService notifications;

  OrderNotificationListener(NotificationService notifications) {
    this.notifications = notifications;
  }

  @ApplicationModuleListener
  public void ready(OrderReady event) {
    notifications.append(event.id(), event.orderId(), Notice.Kind.NEW_ORDER, event.occurredAt());
  }

  @ApplicationModuleListener
  public void reminder(OrderReminderRaised event) {
    notifications.append(
        event.id(), event.orderId(), Notice.Kind.ORDER_REMINDER, event.occurredAt());
  }
}
