package com.hanserwei.hanmenu.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hanserwei.hanmenu.identity.application.EmployeeAdministration;
import com.hanserwei.hanmenu.identity.application.EmployeeAuthentication;
import com.hanserwei.hanmenu.identity.domain.EmployeeProfile;
import com.hanserwei.hanmenu.identity.domain.EmployeeRepository;
import com.hanserwei.hanmenu.identity.domain.NewPassword;
import com.hanserwei.hanmenu.support.TestDatabase;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestExecutionListeners.MergeMode;
import org.springframework.transaction.support.TransactionTemplate;

/** 验证 Hibernate 并发锁、实体抓取计划及 JPA 事件登记的真实事务语义. */
@ApplicationModuleTest
@ActiveProfiles("test")
@DirtiesContext
@TestExecutionListeners(
    listeners = {TestDatabase.Cleanup.class, TestDatabase.RedisCleanup.class},
    mergeMode = MergeMode.MERGE_WITH_DEFAULTS)
class JpaPersistenceIt {
  private static final TestDatabase DATABASE = new TestDatabase(JpaPersistenceIt.class);
  private static final String PASSWORD = "Jpa-Admin-password-2026";
  @Autowired private EntityManagerFactory factory;
  @Autowired private EmployeeAdministration administration;
  @Autowired private EmployeeAuthentication authentication;
  @Autowired private EmployeeRepository employees;
  @Autowired private TransactionTemplate transactions;
  @Autowired private EventPublicationRegistry publications;
  @Autowired private JdbcClient jdbc;
  private UUID employeeId;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    DATABASE.configure(registry);
  }

  @BeforeEach
  void fixtures() {
    jdbc.sql(
            "TRUNCATE event_publication, identity_session,"
                + " identity_audit, identity_employee CASCADE")
        .update();
    administration.bootstrap("admin", new NewPassword(PASSWORD));
    employeeId = employees.findByUsername("admin").orElseThrow().id();
  }

  @Test
  void hibernateVersionRejectsConflictingEntityManagers() {
    try (var first = factory.createEntityManager();
        var second = factory.createEntityManager()) {
      first.getTransaction().begin();
      second.getTransaction().begin();
      try {
        var winner = first.find(EmployeeEntity.class, employeeId);
        var loser = second.find(EmployeeEntity.class, employeeId);
        var winnerState = winner.toDomain();
        var loserState = loser.toDomain();
        winnerState.reviseProfile(new EmployeeProfile("admin", "首先提交", ""), Instant.now());
        loserState.reviseProfile(new EmployeeProfile("admin", "陈旧事务", ""), Instant.now());
        winner.apply(winnerState);
        loser.apply(loserState);
        first.getTransaction().commit();
        assertThatThrownBy(second::flush).isInstanceOf(OptimisticLockException.class);
      } finally {
        if (first.getTransaction().isActive()) {
          first.getTransaction().rollback();
        }
        if (second.getTransaction().isActive()) {
          second.getTransaction().rollback();
        }
      }
    }
    var current = employees.findById(employeeId).orElseThrow();
    assertThat(current.profile().displayName()).isEqualTo("首先提交");
    assertThat(current.version()).isEqualTo(1);
  }

  @Test
  void entityGraphAuthenticatesWithOneDatabaseQuery() {
    String token = authentication.login("admin", PASSWORD, "127.0.0.1").token();
    var statistics = factory.unwrap(SessionFactory.class).getStatistics();
    boolean enabled = statistics.isStatisticsEnabled();
    try {
      statistics.setStatisticsEnabled(true);
      statistics.clear();
      assertThat(authentication.authenticate(token)).isPresent();
      assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    } finally {
      statistics.setStatisticsEnabled(enabled);
    }
  }

  @Test
  void aggregateUpdateAndJpaPublicationRollbackTogether() {
    String original = employees.findById(employeeId).orElseThrow().profile().displayName();
    transactions.executeWithoutResult(
        transaction -> {
          var employee = employees.findById(employeeId).orElseThrow();
          employee.reviseProfile(new EmployeeProfile("admin", "事务内变更", ""), Instant.now());
          employees.update(employee);
          publications.store(
              new PublicationFixture("payload-".repeat(100)),
              Stream.of(PublicationTargetIdentifier.of("test-listener")));
          SharedEntityManagerCreator.createSharedEntityManager(factory).flush();
          assertThat(jdbc.sql("SELECT count(*) FROM event_publication").query(Long.class).single())
              .isEqualTo(1);
          transaction.setRollbackOnly();
        });
    assertThat(employees.findById(employeeId).orElseThrow().profile().displayName())
        .isEqualTo(original);
    assertThat(jdbc.sql("SELECT count(*) FROM event_publication").query(Long.class).single())
        .isZero();
  }

  /** 仅用于基础设施测试的事件，验证长载荷持久化和事务回滚，不构造演示业务. */
  public record PublicationFixture(String content) {}
}
