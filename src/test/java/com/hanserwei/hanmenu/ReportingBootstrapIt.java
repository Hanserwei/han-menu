package com.hanserwei.hanmenu;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanserwei.hanmenu.customer.domain.CustomerAccount;
import com.hanserwei.hanmenu.customer.domain.CustomerRepository;
import com.hanserwei.hanmenu.ordering.domain.AddressSnapshot;
import com.hanserwei.hanmenu.ordering.domain.Order;
import com.hanserwei.hanmenu.ordering.domain.OrderLine;
import com.hanserwei.hanmenu.ordering.domain.OrderRepository;
import com.hanserwei.hanmenu.support.TestDatabase;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestExecutionListeners.MergeMode;
import org.springframework.transaction.support.TransactionTemplate;

/** 验证升级到 P6 首次启动会自动重建此前没有统计事件的业务数据. */
@SpringBootTest(properties = "han-menu.reporting.bootstrap-enabled=true")
@ActiveProfiles("test")
@DirtiesContext
@Import(ReportingBootstrapIt.LegacyFacts.class)
@TestExecutionListeners(
    listeners = {TestDatabase.Cleanup.class, TestDatabase.RedisCleanup.class},
    mergeMode = MergeMode.MERGE_WITH_DEFAULTS)
class ReportingBootstrapIt {
  private static final TestDatabase DATABASE = new TestDatabase(ReportingBootstrapIt.class);
  @Autowired private JdbcClient jdbc;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    DATABASE.configure(registry);
  }

  @Test
  void applicationReadyRebuildsExistingFactsAndPublishesReadyGeneration() {
    assertThat(
            jdbc.sql("SELECT initialized FROM reporting_projection WHERE id = 1")
                .query(Boolean.class)
                .single())
        .isTrue();
    assertThat(
            jdbc.sql("SELECT generation FROM reporting_projection WHERE id = 1")
                .query(Long.class)
                .single())
        .isEqualTo(1);
    assertThat(jdbc.sql("SELECT count(*) FROM reporting_order").query(Long.class).single())
        .isEqualTo(1);
    assertThat(jdbc.sql("SELECT count(*) FROM reporting_customer").query(Long.class).single())
        .isEqualTo(1);
    assertThat(jdbc.sql("SELECT total FROM reporting_order").query(BigDecimal.class).single())
        .isEqualByComparingTo("12.34");
  }

  /** 在 ApplicationReadyEvent 前建立没有新事件的旧阶段事实，模拟已有 P5 数据. */
  @TestConfiguration(proxyBeanMethods = false)
  static class LegacyFacts {
    @Bean
    ApplicationRunner seedLegacy(
        TransactionTemplate transactions, CustomerRepository customers, OrderRepository orders) {
      return arguments ->
          transactions.executeWithoutResult(
              status -> {
                Instant now = Instant.now();
                var customer =
                    CustomerAccount.create(
                        UUID.randomUUID(), "+8613800138066", "旧阶段顾客", "unused-fixture-hash", now);
                customers.add(customer);
                var order =
                    Order.submit(
                        customer.id(),
                        "legacy-key",
                        "digest",
                        new AddressSnapshot(
                            UUID.randomUUID(),
                            0,
                            "测试顾客",
                            customer.phone(),
                            "浙江省",
                            "杭州市",
                            "西湖区",
                            "旧阶段测试地址"),
                        List.of(
                            new OrderLine(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "DISH",
                                "菜品",
                                new BigDecimal("12.34"),
                                1,
                                Map.of(),
                                List.of())),
                        now);
                orders.add(order);
              });
    }
  }
}
