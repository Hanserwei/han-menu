package com.hanserwei.hanmenu.payment.application;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.IncompleteEventPublications;
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
  private final IncompleteEventPublications publications;

  PaymentSchedules(PaymentReconciliation reconciliation, IncompleteEventPublications publications) {
    this.reconciliation = reconciliation;
    this.publications = publications;
  }

  @Scheduled(fixedDelayString = "${han-menu.payment.poll-delay:10000}")
  void reconcile() {
    reconciliation.reconcile();
  }

  @Scheduled(fixedDelay = 60000)
  void recoverEvents() {
    publications.resubmitIncompletePublications(
        org.springframework.modulith.events.ResubmissionOptions.defaults()
            .withMinAge(Duration.ofMinutes(1))
            .withBatchSize(100)
            .withMaxInFlight(10));
  }
}
