package com.hanserwei.hanmenu;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 应用级可靠事件恢复独立于支付、通知轮询开关，按有界批次重投未完成消费者. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
    name = "han-menu.events.recovery-enabled",
    havingValue = "true",
    matchIfMissing = true)
class EventRecoveryConfiguration {
  private final IncompleteEventPublications publications;

  EventRecoveryConfiguration(IncompleteEventPublications publications) {
    this.publications = publications;
  }

  @Scheduled(fixedDelay = 60000)
  void recover() {
    publications.resubmitIncompletePublications(
        ResubmissionOptions.defaults()
            .withMinAge(Duration.ofMinutes(1))
            .withBatchSize(100)
            .withMaxInFlight(10));
  }
}
