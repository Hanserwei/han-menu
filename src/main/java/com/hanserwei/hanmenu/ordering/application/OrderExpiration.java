package com.hanserwei.hanmenu.ordering.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 超时检查不调用渠道，每个订单独立提交取消意图，失败记录在下一轮重新处理. */
@Component
@ConditionalOnProperty(
    name = "han-menu.payment.scheduling-enabled",
    havingValue = "true",
    matchIfMissing = true)
class OrderExpiration {
  private final OrderLifecycleService lifecycle;

  OrderExpiration(OrderLifecycleService lifecycle) {
    this.lifecycle = lifecycle;
  }

  @Scheduled(fixedDelay = 10000)
  void expire() {
    for (var id : lifecycle.expired()) {
      lifecycle.expire(id);
    }
  }
}
