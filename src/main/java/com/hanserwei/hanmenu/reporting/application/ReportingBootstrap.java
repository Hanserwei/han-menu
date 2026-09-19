package com.hanserwei.hanmenu.reporting.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 首次启动初始化已有事实；失败不伪造零报表，后续自动或管理员重试均可恢复. */
@Component
@ConditionalOnProperty(
    name = "han-menu.reporting.bootstrap-enabled",
    havingValue = "true",
    matchIfMissing = true)
class ReportingBootstrap {
  private static final Logger LOGGER = LoggerFactory.getLogger(ReportingBootstrap.class);
  private final ProjectionMaintenance maintenance;

  ReportingBootstrap(ProjectionMaintenance maintenance) {
    this.maintenance = maintenance;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Scheduled(fixedDelay = 60000, initialDelay = 60000)
  public void initialize() {
    try {
      maintenance.initialize();
    } catch (RuntimeException exception) {
      LOGGER.warn(
          "report_projection_initialization_failed exceptionType={}",
          exception.getClass().getSimpleName());
    }
  }
}
