package com.hanserwei.hanmenu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hanserwei.hanmenu.catalog.application.CatalogService;
import com.hanserwei.hanmenu.catalog.domain.DishId;
import com.hanserwei.hanmenu.catalog.domain.DishRepository;
import com.hanserwei.hanmenu.catalog.events.DishPublished;
import com.hanserwei.hanmenu.support.TestDatabase;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestExecutionListeners.MergeMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
@TestExecutionListeners(
    listeners = TestDatabase.Cleanup.class,
    mergeMode = MergeMode.MERGE_WITH_DEFAULTS)
class MenuFlowIt {
  private static final TestDatabase DATABASE = new TestDatabase(MenuFlowIt.class);

  @Autowired private MockMvc mvc;
  @Autowired private JsonMapper json;
  @Autowired private JdbcClient jdbc;
  @Autowired private CatalogService catalog;
  @Autowired private DishRepository dishes;
  @Autowired private ApplicationEventPublisher events;
  @Autowired private TransactionTemplate transactions;
  @Autowired private IncompleteEventPublications publications;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    DATABASE.configure(registry);
  }

  @BeforeEach
  void clearIsolatedSchema() {
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(pendingEvents()).isZero());
    jdbc.sql("TRUNCATE catalog_dish, menu_entry").update();
  }

  @Test
  void createsPublishesAndProjectsThroughHttp() throws Exception {
    String request =
        """
        {"name":"Noodles","price":18.50}
        """;
    var response =
        mvc.perform(post("/api/catalog/dishes").contentType("application/json").content(request))
            .andExpect(status().isCreated())
            .andReturn();
    var id = json.readTree(response.getResponse().getContentAsString()).get("id").asString();
    mvc.perform(get("/api/menu")).andExpect(jsonPath("$").isEmpty());
    mvc.perform(post("/api/catalog/dishes/{id}/publication", id)).andExpect(status().isAccepted());
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                mvc.perform(get("/api/menu"))
                    .andExpect(jsonPath("$[0].dishId").value(id))
                    .andExpect(jsonPath("$[0].price").value(18.50)));
    mvc.perform(post("/api/catalog/dishes/{id}/publication", id)).andExpect(status().isConflict());
  }

  @Test
  void rejectsInvalidInputAndMissingDishes() throws Exception {
    String request =
        """
        {"name":" ","price":-1}
        """;
    mvc.perform(post("/api/catalog/dishes").contentType("application/json").content(request))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/catalog/dishes/{id}/publication", UUID.randomUUID()))
        .andExpect(status().isNotFound());
    assertThat(jdbc.sql("SELECT count(*) FROM catalog_dish").query(Long.class).single()).isZero();
  }

  @Test
  void rollsBackAggregateAndPersistentEventTogether() {
    var id = catalog.create("Rolled back dish", BigDecimal.TEN);
    transactions.executeWithoutResult(
        transaction -> {
          catalog.publish(id);
          assertThat(pendingEvents()).isEqualTo(1);
          transaction.setRollbackOnly();
        });
    assertThat(pendingEvents()).isZero();
    assertThat(
            jdbc.sql("SELECT status FROM catalog_dish WHERE id = ?")
                .param(id)
                .query(String.class)
                .single())
        .isEqualTo("DRAFT");
    assertThat(jdbc.sql("SELECT count(*) FROM menu_entry").query(Long.class).single()).isZero();
  }

  @Test
  void rejectsUpdateFromStaleAggregate() {
    var id = new DishId(catalog.create("Concurrent dish", BigDecimal.TEN));
    var first = dishes.findById(id).orElseThrow();
    var stale = dishes.findById(id).orElseThrow();
    first.publish();
    stale.publish();
    transactions.executeWithoutResult(transaction -> dishes.update(first));
    assertThatThrownBy(() -> transactions.executeWithoutResult(transaction -> dishes.update(stale)))
        .isInstanceOf(OptimisticLockingFailureException.class);
  }

  @Test
  void duplicateDeliveryIsIdempotent() {
    var event =
        new DishPublished(
            UUID.randomUUID(), UUID.randomUUID(), "Repeated", BigDecimal.TEN, Instant.now());
    transactions.executeWithoutResult(transaction -> events.publishEvent(event));
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(menuCount()).isEqualTo(1));
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(pendingEvents()).isZero());
    transactions.executeWithoutResult(transaction -> events.publishEvent(event));
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(pendingEvents()).isZero());
    assertThat(menuCount()).isEqualTo(1);
  }

  @Test
  void failedListenerKeepsDurablePublicationAndCanBeReplayed() {
    jdbc.sql("ALTER TABLE menu_entry ADD CONSTRAINT simulated_outage CHECK (false)").update();
    try {
      var id = catalog.create("Recovered dish", BigDecimal.TEN);
      catalog.publish(id);
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () ->
                  assertThat(
                          jdbc.sql("SELECT count(*) FROM event_publication WHERE status = 'FAILED'")
                              .query(Long.class)
                              .single())
                      .isEqualTo(1));
      assertThat(menuCount()).isZero();
    } finally {
      jdbc.sql("ALTER TABLE menu_entry DROP CONSTRAINT simulated_outage").update();
    }
    publications.resubmitIncompletePublications(publication -> true);
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              assertThat(menuCount()).isEqualTo(1);
              assertThat(pendingEvents()).isZero();
            });
  }

  private long pendingEvents() {
    return jdbc.sql("SELECT count(*) FROM event_publication").query(Long.class).single();
  }

  private long menuCount() {
    return jdbc.sql("SELECT count(*) FROM menu_entry").query(Long.class).single();
  }
}
