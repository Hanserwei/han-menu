package com.hanserwei.hanmenu.notification.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** 发送与会话撤销检查分别调度，慢渠道不能阻塞所有本地维护任务. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
    name = "han-menu.notification.scheduling-enabled",
    havingValue = "true",
    matchIfMissing = true)
class NotificationSchedules {
  private final NotificationDispatcher dispatcher;

  NotificationSchedules(NotificationDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  @Bean
  ThreadPoolTaskScheduler taskScheduler() {
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(4);
    scheduler.setThreadNamePrefix("han-menu-maintenance-");
    scheduler.setWaitForTasksToCompleteOnShutdown(true);
    scheduler.setAwaitTerminationSeconds(10);
    return scheduler;
  }

  @Scheduled(fixedDelay = 5000)
  void dispatch() {
    dispatcher.dispatch();
  }

  @Scheduled(fixedDelay = 5000)
  void prune() {
    dispatcher.prune();
  }
}
