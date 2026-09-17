package com.hanserwei.hanmenu;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanserwei.hanmenu.support.TestDatabase;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestExecutionListeners.MergeMode;
import tools.jackson.databind.json.JsonMapper;

/** 验证真实 HTTP 服务、PostgreSQL 连接和事件基础设施，不引入演示业务对象. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext
@TestExecutionListeners(
    listeners = {TestDatabase.Cleanup.class, TestDatabase.RedisCleanup.class},
    mergeMode = MergeMode.MERGE_WITH_DEFAULTS)
class ApplicationInfrastructureIt {
  private static final TestDatabase DATABASE = new TestDatabase(ApplicationInfrastructureIt.class);

  @Autowired private JdbcClient jdbc;
  @Autowired private Flyway flyway;
  @Autowired private JsonMapper json;

  @Value("${local.server.port}")
  private int port;

  /** 为本测试上下文绑定独立 schema，避免迁移和测试数据污染开发库. */
  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    DATABASE.configure(registry);
  }

  /** 通过真实网络请求检查启动结果，健康状态同时包含数据库连接检查. */
  @Test
  void bootsHttpServerWithHealthyPostgresConnection() throws Exception {
    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      var request =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/health"))
              .timeout(Duration.ofSeconds(5))
              .GET()
              .build();
      var response = client.send(request, BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(json.readTree(response.body()).get("status").asString()).isEqualTo("UP");
    }
  }

  /** 验证全新数据库基线与 ORM 事件基础设施，不创建演示表或兼容结构. */
  @Test
  void appliesMigrationsAndProvidesPersistentEventInfrastructure() {
    flyway.validate();
    assertThat(flyway.info().pending()).isEmpty();
    assertThat(jdbc.sql("SELECT count(*) FROM event_publication").query(Long.class).single())
        .isZero();
    assertThat(
            jdbc.sql(
                    "SELECT table_name FROM information_schema.tables"
                        + " WHERE table_schema = current_schema()")
                .query(String.class)
                .list())
        .containsExactlyInAnyOrder(
            "event_publication",
            "flyway_schema_history",
            "identity_employee",
            "identity_session",
            "identity_audit");
  }
}
