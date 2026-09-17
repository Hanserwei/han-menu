package com.hanserwei.hanmenu.support;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/** Isolates each integration test context in a disposable PostgreSQL schema. */
public final class TestDatabase implements AutoCloseable {
  private static final ConcurrentHashMap<Class<?>, TestDatabase> DATABASES =
      new ConcurrentHashMap<>();
  private final String schema = "test_" + UUID.randomUUID().toString().replace("-", "");
  private final String url;
  private final String username;
  private final String password;

  /** Creates an isolated schema in a database explicitly named for testing. */
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

  /** Points the connection pool and Flyway at this test context's schema. */
  public void configure(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> url);
    registry.add("spring.datasource.username", () -> username);
    registry.add("spring.datasource.password", () -> password);
    registry.add("spring.datasource.hikari.schema", () -> schema);
    registry.add("spring.flyway.default-schema", () -> schema);
  }

  @Override
  public void close() {
    execute("DROP SCHEMA " + schema + " CASCADE");
  }

  private void execute(String sql) {
    try (var connection = DriverManager.getConnection(url, username, password);
        var statement = connection.createStatement()) {
      statement.execute(sql);
    } catch (SQLException exception) {
      throw new IllegalStateException(
          "Could not prepare isolated PostgreSQL test schema", exception);
    }
  }

  /** Drops schemas after Spring has closed the context and its event publication registry. */
  public static final class Cleanup extends AbstractTestExecutionListener {
    @Override
    public int getOrder() {
      // After callbacks run in reverse: DirtiesContext (3000) closes the context first.
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
}
