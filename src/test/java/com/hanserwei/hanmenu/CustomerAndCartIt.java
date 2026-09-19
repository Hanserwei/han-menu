package com.hanserwei.hanmenu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hanserwei.hanmenu.catalog.application.CatalogAdministration;
import com.hanserwei.hanmenu.catalog.domain.FlavorGroup;
import com.hanserwei.hanmenu.catalog.domain.Money;
import com.hanserwei.hanmenu.catalog.domain.ProductKind;
import com.hanserwei.hanmenu.customer.application.AddressBookService;
import com.hanserwei.hanmenu.customer.application.CustomerTokenFactory;
import com.hanserwei.hanmenu.customer.domain.CustomerRepository;
import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import com.hanserwei.hanmenu.identity.application.EmployeeAdministration;
import com.hanserwei.hanmenu.identity.application.EmployeeAuthentication;
import com.hanserwei.hanmenu.identity.domain.NewPassword;
import com.hanserwei.hanmenu.support.TestDatabase;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestExecutionListeners.MergeMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** P3 真实数据库及 Redis 纵向验收，覆盖身份隔离、水平越权与并发事务. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
@ExtendWith(OutputCaptureExtension.class)
@TestExecutionListeners(
    listeners = {TestDatabase.Cleanup.class, TestDatabase.RedisCleanup.class},
    mergeMode = MergeMode.MERGE_WITH_DEFAULTS)
class CustomerAndCartIt {
  private static final TestDatabase DATABASE = new TestDatabase(CustomerAndCartIt.class);
  private static final String PASSWORD = "Customer-password-2026";
  private static final String PHONE = "13800138001";
  private static final String OTHER_PHONE = "13800138002";
  @Autowired private MockMvc mvc;
  @Autowired private JsonMapper json;
  @Autowired private JdbcClient jdbc;
  @Autowired private StringRedisTemplate redis;
  @Autowired private EmployeeAdministration employees;
  @Autowired private EmployeeAuthentication staffAuthentication;
  @Autowired private CustomerRepository customers;
  @Autowired private CatalogAdministration catalog;
  @Autowired private CustomerTokenFactory tokens;
  @Autowired private AddressBookService addresses;
  @Autowired private TransactionTemplate transactions;

  @Value("${han-menu.customer.redis-key-prefix}")
  private String redisPrefix;

  private String token;
  private String otherToken;
  private String staffToken;
  private StaffIdentity staff;
  private UUID customerId;
  private UUID dish;
  private UUID category;

  /** 在测试替身重置或 schema 清理前等待派生事件，保留真实异步语义. */
  @org.junit.jupiter.api.AfterEach
  void awaitEvents() throws InterruptedException {
    TestDatabase.awaitPublications(jdbc);
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    DATABASE.configure(registry);
  }

  /** 清理仅属于本上下文的 schema；顾客限流键使用独立测试前缀. */
  @BeforeEach
  void fixtures() throws Exception {
    jdbc.sql(
            "TRUNCATE cart_item, cart, customer_address, customer_session, customer_account,"
                + " catalog_meal_component, catalog_product, catalog_category,"
                + " identity_session, identity_employee, identity_audit CASCADE")
        .update();
    clearRateKeys();
    employees.bootstrap("admin", new NewPassword(PASSWORD));
    var admin = staffAuthentication.login("admin", PASSWORD, UUID.randomUUID().toString());
    staffToken = admin.token();
    staff = admin.identity();
    category = catalog.createCategory(staff, ProductKind.DISH, "菜品", 0).id();
    dish =
        catalog
            .createProduct(
                staff,
                ProductKind.DISH,
                category,
                "面条",
                "",
                new Money(new BigDecimal("18.50")),
                null,
                List.of(new FlavorGroup("辣度", List.of("微辣", "不辣"), true)),
                List.of())
            .id();
    catalog.changeSale(staff, dish, true, 0);
    customerId =
        UUID.fromString(
            body(register(PHONE).andExpect(status().isCreated()).andReturn())
                .path("id")
                .asString());
    register(OTHER_PHONE).andExpect(status().isCreated());
    token = login(PHONE);
    otherToken = login(OTHER_PHONE);
  }

  @Test
  void customerAndEmployeeSessionsCannotCrossBoundaries() throws Exception {
    mvc.perform(auth(get("/api/v1/customer/me"), token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phone").value("+" + PHONE));
    mvc.perform(auth(get("/api/v1/customer/me"), staffToken)).andExpect(status().isUnauthorized());
    mvc.perform(auth(get("/api/v1/cart"), staffToken)).andExpect(status().isUnauthorized());
    mvc.perform(auth(get("/api/v1/me"), token)).andExpect(status().isUnauthorized());
    mvc.perform(auth(get("/api/v1/catalog/products"), token)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/customer/me")).andExpect(status().isUnauthorized());
    assertThat(token).startsWith("hmc_");
    assertThat(staffToken).startsWith("hme_");
    mvc.perform(auth(get("/api/v1/menu/products"), token)).andExpect(status().isOk());
    mvc.perform(auth(get("/api/v1/storefront"), token)).andExpect(status().isOk());
    assertThat(
            jdbc.sql("SELECT token_hash FROM customer_session WHERE customer_id = ?")
                .param(customerId)
                .query(String.class)
                .single())
        .isEqualTo(tokens.digest(token));
    assertThat(customers.findById(customerId).orElseThrow().passwordHash())
        .startsWith("$2a$12$")
        .doesNotContain(PASSWORD);
  }

  @Test
  void profileVersionsAndExplicitDefaultsAreReturnedCorrectly() throws Exception {
    mvc.perform(
            auth(put("/api/v1/customer/me"), token)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("displayName", "新昵称", "version", 0))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
    mvc.perform(
            auth(put("/api/v1/customer/me"), token)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("displayName", "旧页面", "version", 0))))
        .andExpect(status().isConflict());
    var first = newAddress(token, true);
    var second = newAddress(token, true);
    mvc.perform(auth(get("/api/v1/customer/addresses/{id}", first), token))
        .andExpect(jsonPath("$.defaultAddress").value(false))
        .andExpect(jsonPath("$.version").value(1));
    mvc.perform(auth(get("/api/v1/customer/addresses/{id}", second), token))
        .andExpect(jsonPath("$.defaultAddress").value(true));
    makeDefault(first, token, 1)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2));
    mvc.perform(
            auth(put("/api/v1/customer/addresses/{id}", first), token)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("address", address(false), "version", 2))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaultAddress").value(false))
        .andExpect(jsonPath("$.version").value(3));
    assertThat(defaultCount()).isZero();
  }

  @Test
  void addressesAreOwnedAndOtherCustomerIdsAreNotDiscoverable() throws Exception {
    String id = newAddress(token, true);
    mvc.perform(auth(get("/api/v1/customer/addresses/{id}", id), otherToken))
        .andExpect(status().isNotFound());
    mvc.perform(
            auth(put("/api/v1/customer/addresses/{id}", id), otherToken)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("address", address(false), "version", 0))))
        .andExpect(status().isNotFound());
    makeDefault(id, otherToken, 0).andExpect(status().isNotFound());
    mvc.perform(
            auth(delete("/api/v1/customer/addresses/{id}", id), otherToken).param("version", "0"))
        .andExpect(status().isNotFound());
    mvc.perform(auth(get("/api/v1/customer/addresses"), otherToken))
        .andExpect(jsonPath("$").isEmpty());
    mvc.perform(auth(delete("/api/v1/customer/addresses/{id}", id), token))
        .andExpect(status().isBadRequest());
    mvc.perform(auth(delete("/api/v1/customer/addresses/{id}", id), token).param("version", "0"))
        .andExpect(status().isNoContent());
  }

  @Test
  void concurrentDefaultCreationLeavesExactlyOneDefault() throws Exception {
    var gate = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first =
          executor.submit(
              () -> {
                gate.await();
                return createAddress(token, true).andReturn().getResponse().getStatus();
              });
      var second =
          executor.submit(
              () -> {
                gate.await();
                return createAddress(token, true).andReturn().getResponse().getStatus();
              });
      gate.countDown();
      assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(201);
      assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(201);
    }
    assertThat(defaultCount()).isEqualTo(1);
  }

  @Test
  void addressesHaveLimitAndDefaultChangesRollbackTogether() throws Exception {
    String original = newAddress(token, true);
    var identity = new com.hanserwei.hanmenu.customer.api.CustomerIdentity(customerId, "", "", 0);
    transactions.executeWithoutResult(
        transaction -> {
          addresses.create(
              identity,
              new AddressBookService.AddressCommand(
                  "公司", "收件人", PHONE, "浙江", "杭州", "西湖", "临时地址", true));
          assertThat(defaultCount()).isEqualTo(1);
          transaction.setRollbackOnly();
        });
    mvc.perform(auth(get("/api/v1/customer/addresses/{id}", original), token))
        .andExpect(jsonPath("$.defaultAddress").value(true))
        .andExpect(jsonPath("$.version").value(0));
    for (int index = 1; index < 20; index++) {
      createAddress(token, false).andExpect(status().isCreated());
    }
    createAddress(token, true).andExpect(status().isConflict());
    assertThat(defaultCount()).isEqualTo(1);
    assertThat(
            jdbc.sql("SELECT count(*) FROM customer_address WHERE customer_id = ?")
                .param(customerId)
                .query(Long.class)
                .single())
        .isEqualTo(20);
  }

  @Test
  void passwordChangeLogoutAndExpiryRevokeCustomerSessions() throws Exception {
    String another = login(PHONE);
    mvc.perform(auth(delete("/api/v1/customer/sessions/current"), token))
        .andExpect(status().isNoContent());
    mvc.perform(auth(get("/api/v1/customer/me"), token)).andExpect(status().isUnauthorized());
    mvc.perform(auth(get("/api/v1/customer/me"), another)).andExpect(status().isOk());
    mvc.perform(
            auth(put("/api/v1/customer/me/password"), another)
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "currentPassword",
                            "wrong",
                            "newPassword",
                            "Replacement-password-2026"))))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            auth(put("/api/v1/customer/me/password"), another)
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "currentPassword",
                            PASSWORD,
                            "newPassword",
                            "Replacement-password-2026"))))
        .andExpect(status().isNoContent());
    mvc.perform(auth(get("/api/v1/customer/me"), another)).andExpect(status().isUnauthorized());
    var logged =
        mvc.perform(
                post("/api/v1/customer/sessions")
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of("phone", PHONE, "password", "Replacement-password-2026"))))
            .andExpect(status().isCreated())
            .andReturn();
    String replacement = body(logged).path("accessToken").asString();
    jdbc.sql("UPDATE customer_session SET expires_at = ? WHERE customer_id = ?")
        .params(Timestamp.from(Instant.now().minusSeconds(1)), customerId)
        .update();
    mvc.perform(auth(get("/api/v1/customer/me"), replacement)).andExpect(status().isUnauthorized());
  }

  @Test
  void disabledCustomersCannotAccessCartOrAddressesAndReenableDoesNotReviveToken()
      throws Exception {
    transactions.executeWithoutResult(
        tx -> {
          var account = customers.findById(customerId).orElseThrow();
          account.changeEnabled(false, Instant.now());
          customers.update(account);
        });
    mvc.perform(auth(get("/api/v1/cart"), token)).andExpect(status().isUnauthorized());
    mvc.perform(auth(get("/api/v1/customer/addresses"), token))
        .andExpect(status().isUnauthorized());
    transactions.executeWithoutResult(
        tx -> {
          var account = customers.findById(customerId).orElseThrow();
          account.changeEnabled(true, Instant.now());
          customers.update(account);
        });
    mvc.perform(auth(get("/api/v1/customer/me"), token)).andExpect(status().isUnauthorized());
    mvc.perform(auth(get("/api/v1/customer/me"), login(PHONE))).andExpect(status().isOk());
  }

  @Test
  void cartMergesSelectionsAndFirstWriteVersionPreventsReplay() throws Exception {
    mvc.perform(auth(get("/api/v1/cart"), token))
        .andExpect(jsonPath("$.version").value(0))
        .andExpect(jsonPath("$.items").isEmpty());
    assertThat(jdbc.sql("SELECT count(*) FROM cart").query(Long.class).single()).isZero();
    var first =
        add(token, 2, "微辣", 0)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.estimatedTotal").value(37.0))
            .andReturn();
    String id = body(first).path("items").get(0).path("id").asString();
    add(token, 2, "微辣", 0).andExpect(status().isConflict());
    add(token, 1, "微辣", 1)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(id))
        .andExpect(jsonPath("$.items[0].quantity").value(3))
        .andExpect(jsonPath("$.version").value(2));
    add(token, 1, "不辣", 2)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2));
    mvc.perform(
            auth(patch("/api/v1/cart/items/{id}", id), token)
                .contentType("application/json")
                .content("{\"quantity\":5,\"version\":3}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(4));
    mvc.perform(auth(delete("/api/v1/cart/items/{id}", id), token).param("version", "4"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1));
    mvc.perform(auth(delete("/api/v1/cart/items"), token).param("version", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.version").value(6));
    add(token, 1, "微辣", 0).andExpect(status().isConflict());
  }

  @Test
  void cartRejectsInjectedPriceUnknownFlavorsMissingVersionsAndForeignItems() throws Exception {
    mvc.perform(
            auth(post("/api/v1/cart/items"), token)
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "productId",
                            dish,
                            "quantity",
                            1,
                            "selections",
                            Map.of("辣度", "微辣"),
                            "version",
                            0,
                            "unitPrice",
                            0.01))))
        .andExpect(status().isBadRequest());
    mvc.perform(
            auth(post("/api/v1/cart/items"), token)
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "productId", dish, "quantity", 1, "selections", Map.of("辣度", "微辣")))))
        .andExpect(status().isBadRequest());
    mvc.perform(
            auth(post("/api/v1/cart/items"), token)
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "productId",
                            dish,
                            "quantity",
                            1,
                            "selections",
                            Map.of("未知", "值"),
                            "version",
                            0))))
        .andExpect(status().isBadRequest());
    var first = add(token, 1, "微辣", 0).andExpect(status().isOk()).andReturn();
    String id = body(first).path("items").get(0).path("id").asString();
    add(otherToken, 1, "不辣", 0).andExpect(status().isOk());
    mvc.perform(auth(delete("/api/v1/cart/items/{id}", id), otherToken).param("version", "1"))
        .andExpect(status().isNotFound());
    mvc.perform(auth(get("/api/v1/cart"), token))
        .andExpect(jsonPath("$.items[0].quantity").value(1));
    add(token, 99, "微辣", 1).andExpect(status().isBadRequest());
  }

  @Test
  void cartShowsLivePricesAndUnavailableItemsWithoutTreatingSnapshotsAsOrderPrices()
      throws Exception {
    add(token, 2, "微辣", 0).andExpect(status().isOk());
    catalog.changeSale(staff, dish, false, 1);
    mvc.perform(auth(get("/api/v1/cart"), token))
        .andExpect(jsonPath("$.items[0].available").value(false))
        .andExpect(jsonPath("$.items[0].unavailableReason").value("PRODUCT_UNAVAILABLE"))
        .andExpect(jsonPath("$.estimatedTotal").value(0));
    catalog.updateProduct(
        staff,
        dish,
        category,
        "改价面条",
        "",
        new Money(new BigDecimal("25.00")),
        null,
        List.of(new FlavorGroup("辣度", List.of("微辣", "不辣"), true)),
        List.of(),
        2);
    catalog.changeSale(staff, dish, true, 3);
    mvc.perform(auth(get("/api/v1/cart"), token))
        .andExpect(jsonPath("$.items[0].name").value("改价面条"))
        .andExpect(jsonPath("$.items[0].unitPrice").value(25.0))
        .andExpect(jsonPath("$.estimatedTotal").value(50.0));
    catalog.changeSale(staff, dish, false, 4);
    catalog.updateProduct(
        staff,
        dish,
        category,
        "改口味面条",
        "",
        new Money(new BigDecimal("25.00")),
        null,
        List.of(new FlavorGroup("辣度", List.of("重辣"), true)),
        List.of(),
        5);
    catalog.changeSale(staff, dish, true, 6);
    mvc.perform(auth(get("/api/v1/cart"), token))
        .andExpect(jsonPath("$.items[0].unavailableReason").value("SELECTION_UNAVAILABLE"));
    mvc.perform(auth(delete("/api/v1/cart/items"), token).param("version", "1"))
        .andExpect(status().isOk());
  }

  @Test
  void concurrentFirstAddsAndQuantityUpdatesCannotLoseOrDoubleApplyChanges() throws Exception {
    var gate = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first =
          executor.submit(
              () -> {
                gate.await();
                return add(token, 1, "微辣", 0).andReturn().getResponse().getStatus();
              });
      var second =
          executor.submit(
              () -> {
                gate.await();
                return add(token, 1, "微辣", 0).andReturn().getResponse().getStatus();
              });
      gate.countDown();
      assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
    var updated = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first =
          executor.submit(
              () -> {
                updated.await();
                return add(token, 2, "微辣", 1).andReturn().getResponse().getStatus();
              });
      var second =
          executor.submit(
              () -> {
                updated.await();
                return add(token, 3, "微辣", 1).andReturn().getResponse().getStatus();
              });
      updated.countDown();
      assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
    var result =
        mvc.perform(auth(get("/api/v1/cart"), token))
            .andExpect(jsonPath("$.version").value(2))
            .andReturn();
    assertThat(body(result).path("items").get(0).path("quantity").asInt()).isIn(3, 4);
  }

  @Test
  void authRateLimitsAreIsolatedFromEmployeeKeysAndDoNotExposeCredentials(CapturedOutput output)
      throws Exception {
    clearRateKeys();
    for (int index = 0; index < 10; index++) {
      mvc.perform(
              post("/api/v1/customer/sessions")
                  .header("X-Forwarded-For", "192.0.2." + index)
                  .contentType("application/json")
                  .content(
                      json.writeValueAsString(
                          Map.of("phone", "13900139999", "password", "wrong-secret"))))
          .andExpect(status().isUnauthorized());
    }
    mvc.perform(
            post("/api/v1/customer/sessions")
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("phone", "13900139999", "password", "wrong-secret"))))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "60"));
    try (var cursor = redis.scan(ScanOptions.scanOptions().match(redisPrefix + "*").build())) {
      cursor.forEachRemaining(
          key -> {
            assertThat(key).doesNotContain("13900139999", "127.0.0.1");
            assertThat(redis.getExpire(key)).isBetween(1L, 60L);
          });
    }
    assertThat(output.getAll()).doesNotContain(PASSWORD, token, OTHER_PHONE, PHONE, "wrong-secret");
  }

  @Test
  void duplicateRegistrationUnknownFieldsAndBrokenStorageFailClearly() throws Exception {
    register("+" + PHONE).andExpect(status().isConflict());
    mvc.perform(
            post("/api/v1/customer/accounts")
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "phone",
                            "13900130000",
                            "password",
                            PASSWORD,
                            "displayName",
                            "新顾客",
                            "role",
                            "ADMIN"))))
        .andExpect(status().isBadRequest());
    jdbc.sql("ALTER TABLE customer_session RENAME TO unavailable_customer_session").update();
    try {
      mvc.perform(auth(get("/api/v1/customer/me"), token))
          .andExpect(status().isServiceUnavailable());
    } finally {
      jdbc.sql("ALTER TABLE unavailable_customer_session RENAME TO customer_session").update();
    }
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/cart/items']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/customer/accounts']").exists());
  }

  private ResultActions register(String phone) throws Exception {
    return mvc.perform(
        post("/api/v1/customer/accounts")
            .contentType("application/json")
            .content(
                json.writeValueAsString(
                    Map.of("phone", phone, "displayName", "测试顾客", "password", PASSWORD))));
  }

  private String login(String phone) throws Exception {
    return body(mvc.perform(
                post("/api/v1/customer/sessions")
                    .contentType("application/json")
                    .content(json.writeValueAsString(Map.of("phone", phone, "password", PASSWORD))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andReturn())
        .path("accessToken")
        .asString();
  }

  private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String bearer) {
    return request.header("Authorization", "Bearer " + bearer);
  }

  private Map<String, Object> address(boolean selected) {
    return Map.of(
        "label",
        "家",
        "recipientName",
        "收货人",
        "phone",
        PHONE,
        "province",
        "浙江",
        "city",
        "杭州",
        "district",
        "西湖",
        "detail",
        "测试地址一号",
        "defaultAddress",
        selected);
  }

  private ResultActions createAddress(String bearer, boolean selected) throws Exception {
    return mvc.perform(
        auth(post("/api/v1/customer/addresses"), bearer)
            .contentType("application/json")
            .content(json.writeValueAsString(address(selected))));
  }

  private String newAddress(String bearer, boolean selected) throws Exception {
    return body(createAddress(bearer, selected).andExpect(status().isCreated()).andReturn())
        .path("id")
        .asString();
  }

  private ResultActions makeDefault(String id, String bearer, long version) throws Exception {
    return mvc.perform(
        auth(patch("/api/v1/customer/addresses/{id}/default", id), bearer)
            .contentType("application/json")
            .content(json.writeValueAsString(Map.of("version", version))));
  }

  private ResultActions add(String bearer, int quantity, String flavor, long version)
      throws Exception {
    return mvc.perform(
        auth(post("/api/v1/cart/items"), bearer)
            .contentType("application/json")
            .content(
                json.writeValueAsString(
                    Map.of(
                        "productId",
                        dish,
                        "quantity",
                        quantity,
                        "selections",
                        Map.of("辣度", flavor),
                        "version",
                        version))));
  }

  private long defaultCount() {
    return jdbc.sql("SELECT count(*) FROM customer_address WHERE customer_id = ? AND is_default")
        .param(customerId)
        .query(Long.class)
        .single();
  }

  private JsonNode body(MvcResult result) throws Exception {
    return json.readTree(result.getResponse().getContentAsString());
  }

  private void clearRateKeys() {
    try (var keys =
        redis.scan(ScanOptions.scanOptions().match(redisPrefix + "*").count(100).build())) {
      keys.forEachRemaining(redis::delete);
    }
    try (var keys =
        redis.scan(
            ScanOptions.scanOptions()
                .match(redisPrefix.replace(":customer:", ":login:") + "*")
                .count(100)
                .build())) {
      keys.forEachRemaining(redis::delete);
    }
  }
}
