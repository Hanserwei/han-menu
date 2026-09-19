package com.hanserwei.hanmenu.support;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * 集成测试专用的 PostgreSQL schema 资源.
 *
 * <p>每个实例持有自己的连接配置和随机 schema 名称，统一负责创建、配置和释放资源。测试数据库名必须以 {@code _test} 结尾，避免误将开发库用于测试。
 */
public final class TestDatabase implements AutoCloseable {
  private static final ConcurrentHashMap<Class<?>, TestDatabase> DATABASES =
      new ConcurrentHashMap<>();
  private final String schema = "test_" + UUID.randomUUID().toString().replace("-", "");
  private final String url;
  private final String username;
  private final String password;

  /**
   * 在测试数据库中创建隔离 schema，并登记负责释放它的测试类.
   *
   * @param owner 拥有该资源的测试类，每个测试上下文仅创建一个实例
   * @throws IllegalArgumentException 数据库名称不符合测试库约定时抛出
   * @throws IllegalStateException 数据库连接失败或无法创建 schema 时抛出
   */
  public TestDatabase(Class<?> owner) {
    url =
        System.getenv()
            .getOrDefault("TEST_DB_URL", "jdbc:postgresql://localhost:5432/han_menu_test");
    username = System.getenv().getOrDefault("TEST_DB_USERNAME", "han_menu");
    password = Objects.requireNonNull(System.getenv("TEST_DB_PASSWORD"), "Set TEST_DB_PASSWORD");
    if (!url.matches("jdbc:postgresql://[^/]+/[^?]*_test(\\?.*)?")) {
      throw new IllegalArgumentException("Integration database name must end in _test");
    }
    execute("CREATE SCHEMA " + schema);
    DATABASES.put(owner, this);
  }

  /**
   * 将连接池与 Flyway 指向同一个隔离 schema，保证迁移与业务 SQL 使用相同的命名空间.
   *
   * @param registry 当前测试上下文的动态属性注册器
   */
  public void configure(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> url);
    registry.add("spring.datasource.username", () -> username);
    registry.add("spring.datasource.password", () -> password);
    registry.add("spring.datasource.hikari.schema", () -> schema);
    registry.add("spring.flyway.default-schema", () -> schema);
    registry.add("han-menu.identity.redis-key-prefix", () -> "han-menu:test:" + schema + ":login:");
    registry.add(
        "han-menu.customer.redis-key-prefix", () -> "han-menu:test:" + schema + ":customer:");
    registry.add("han-menu.catalog.cache-prefix", () -> "han-menu:test:" + schema + ":catalog:");
    registry.add(
        "han-menu.catalog.storage.bucket",
        () -> System.getenv().getOrDefault("RUSTFS_TEST_BUCKET", "han-menu-test"));
    registry.add("han-menu.catalog.storage.key-prefix", () -> "han-menu-test/" + schema + "/");
  }

  /** 等待可靠事件及其派生事件完成，避免测试清理或统计采样与后台消费者竞争. */
  public static void awaitPublications(org.springframework.jdbc.core.simple.JdbcClient jdbc)
      throws InterruptedException {
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
    while (jdbc.sql("SELECT count(*) FROM event_publication").query(Long.class).single() != 0) {
      if (System.nanoTime() >= deadline) {
        throw new AssertionError("可靠事件未在测试期限内消费完成");
      }
      Thread.sleep(10);
    }
  }

  /** 释放本实例创建的 schema；必须在 Spring 上下文完成关闭之后调用. */
  @Override
  public void close() {
    execute("DROP SCHEMA " + schema + " CASCADE");
  }

  /** 执行内部生成的 schema 管理语句；schema 名称只含固定前缀和随机十六进制字符. */
  private void execute(String sql) {
    try (var connection = DriverManager.getConnection(url, username, password);
        var statement = connection.createStatement()) {
      statement.execute(sql);
    } catch (SQLException exception) {
      throw new IllegalStateException(
          "Could not prepare isolated PostgreSQL test schema", exception);
    }
  }

  /** 等待 Spring 关闭连接池与事件登记组件后，再释放测试 schema. */
  public static final class Cleanup extends AbstractTestExecutionListener {
    @Override
    public int getOrder() {
      // 结束回调逆序执行：DirtiesContext 的顺序为 3000，因此本监听器会在上下文关闭后执行。
      return 2500;
    }

    @Override
    public void afterTestClass(TestContext testContext) {
      var database = DATABASES.remove(testContext.getTestClass());
      if (database != null) {
        database.close();
      }
    }
  }

  /** 在 Spring 关闭 Redis 连接之前，仅清理本测试上下文的限流键. */
  public static final class RedisCleanup extends AbstractTestExecutionListener {
    @Override
    public int getOrder() {
      return 3500;
    }

    @Override
    public void afterTestClass(TestContext testContext) {
      if (!testContext.hasApplicationContext()) {
        return;
      }
      var context = testContext.getApplicationContext();
      String prefix =
          context
              .getEnvironment()
              .getRequiredProperty("han-menu.identity.redis-key-prefix")
              .replace(":login:", ":");
      if (!prefix.startsWith("han-menu:test:")) {
        throw new IllegalStateException("拒绝清理非测试 Redis 命名空间");
      }
      var redis = context.getBean(StringRedisTemplate.class);
      try (var keys =
          redis.scan(ScanOptions.scanOptions().match(prefix + "*").count(100).build())) {
        keys.forEachRemaining(redis::delete);
      }
    }
  }
}
