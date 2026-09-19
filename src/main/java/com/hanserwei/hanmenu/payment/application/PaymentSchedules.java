package com.hanserwei.hanmenu.payment.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 单实例周期唤醒持久化任务，行锁和处理窗口仍允许多实例安全领取. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
    name = "han-menu.payment.scheduling-enabled",
    havingValue = "true",
    matchIfMissing = true)
class PaymentSchedules {
  private final PaymentReconciliation reconciliation;

  PaymentSchedules(PaymentReconciliation reconciliation) {
    this.reconciliation = reconciliation;
  }

  @Scheduled(fixedDelayString = "${han-menu.payment.poll-delay:10000}")
  void reconcile() {
    reconciliation.reconcile();
  }
}
