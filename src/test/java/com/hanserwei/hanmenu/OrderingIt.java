package com.hanserwei.hanmenu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hanserwei.hanmenu.cart.api.CartCheckout;
import com.hanserwei.hanmenu.cart.application.CartService;
import com.hanserwei.hanmenu.catalog.application.CatalogAdministration;
import com.hanserwei.hanmenu.catalog.domain.FlavorGroup;
import com.hanserwei.hanmenu.catalog.domain.MealComponent;
import com.hanserwei.hanmenu.catalog.domain.Money;
import com.hanserwei.hanmenu.catalog.domain.ProductKind;
import com.hanserwei.hanmenu.customer.api.CustomerIdentity;
import com.hanserwei.hanmenu.customer.application.AddressBookService;
import com.hanserwei.hanmenu.customer.application.CustomerAuthentication;
import com.hanserwei.hanmenu.customer.domain.CustomerPassword;
import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import com.hanserwei.hanmenu.identity.application.EmployeeAdministration;
import com.hanserwei.hanmenu.identity.application.EmployeeAuthentication;
import com.hanserwei.hanmenu.identity.domain.NewPassword;
import com.hanserwei.hanmenu.ordering.application.OrderService;
import com.hanserwei.hanmenu.shop.application.ShopService;
import com.hanserwei.hanmenu.support.TestDatabase;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestExecutionListeners.MergeMode;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** P4 真实 PostgreSQL 纵向验收，覆盖结算、快照、归属、幂等及并发回滚. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
@TestExecutionListeners(
    listeners = {TestDatabase.Cleanup.class, TestDatabase.RedisCleanup.class},
    mergeMode = MergeMode.MERGE_WITH_DEFAULTS)
class OrderingIt {
  private static final TestDatabase DATABASE = new TestDatabase(OrderingIt.class);
  private static final String PASSWORD = "Ordering-password-2026";
  @Autowired private MockMvc mvc;
  @Autowired private JsonMapper json;
  @Autowired private JdbcClient jdbc;
  @Autowired private EmployeeAdministration employees;
  @Autowired private EmployeeAuthentication staffAuthentication;
  @Autowired private CustomerAuthentication authentication;
  @Autowired private AddressBookService addresses;
  @Autowired private CartService carts;
  @Autowired private CatalogAdministration catalog;
  @Autowired private ShopService shop;
  @Autowired private OrderService orders;
  @Autowired private TransactionTemplate transactions;
  @Autowired private jakarta.persistence.EntityManagerFactory entityManagerFactory;
  @MockitoSpyBean private CartCheckout checkout;
  private CustomerIdentity customer;
  private CustomerIdentity other;
  private String token;
  private String otherToken;
  private String staffToken;
  private StaffIdentity staff;
  private UUID addressId;
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

  /** 所有测试数据仅存在于随机测试 schema，登录限流使用随机号码和来源. */
  @BeforeEach
  void fixtures() {
    jdbc.sql(
            "TRUNCATE ordering_line, ordering_order, cart_item, cart, customer_address,"
                + " customer_session, customer_account,"
                + " catalog_meal_component, catalog_product, catalog_category,"
                + " identity_session, identity_employee, identity_audit CASCADE")
        .update();
    String username = "admin_" + UUID.randomUUID().toString().substring(0, 8);
    employees.bootstrap(username, new NewPassword(PASSWORD));
    var employee = staffAuthentication.login(username, PASSWORD, UUID.randomUUID().toString());
    staff = employee.identity();
    staffToken = employee.token();
    var first = customer();
    customer = first.identity();
    token = first.accessToken();
    var second = customer();
    other = second.identity();
    otherToken = second.accessToken();
    addressId = addresses.create(customer, address("示例路 1 号")).id();
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
                flavors(),
                List.of())
            .id();
    catalog.changeSale(staff, dish, true, 0);
    var current = shop.current();
    if (current.status().equals("OPEN")) {
      shop.changeStatus(staff, false, current.version());
    }
    current = shop.current();
    shop.revise(staff, "测试店", "13800138000", "测试店地址", current.version());
    shop.changeStatus(staff, true, shop.current().version());
  }

  @Test
  void submissionRepricesAndOnlySettlesSelectedLinesThenKeepsHistoricalSnapshots()
      throws Exception {
    var cart = add(2, "微辣", 0);
    UUID selected = cart.items().getFirst().id();
    cart = add(1, "不辣", cart.version());
    changeDish("新面条", "21.30", flavors());
    var request = command(cart.version(), List.of(selected));
    var response =
        submit(token, "submit-1", request)
            .andExpect(status().isCreated())
            .andExpect(header().string("Idempotency-Replayed", "false"))
            .andExpect(jsonPath("$.total").value(42.60))
            .andExpect(jsonPath("$.items[0].name").value("新面条"))
            .andExpect(jsonPath("$.status").value("UNPAID"));
    final var order = body(response);
    assertThat(carts.get(customer).items()).hasSize(1);
    assertThat(carts.get(customer).items().getFirst().selections()).containsEntry("辣度", "不辣");
    assertThat(carts.get(customer).version()).isGreaterThan(cart.version());
    addresses.update(customer, addressId, address("修改后的地址"), 0);
    addresses.delete(customer, addressId, 1);
    changeDish("后来的名称", "30.00", flavors());
    catalog.changeSale(staff, dish, false, catalog.get(staff, dish).version());
    catalog.deleteProduct(staff, dish, catalog.get(staff, dish).version());
    mvc.perform(auth(get("/api/v1/orders/{id}", order.path("id").asString()), token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(42.60))
        .andExpect(jsonPath("$.items[0].name").value("新面条"))
        .andExpect(jsonPath("$.address.detail").value("示例路 1 号"));
    submit(token, "submit-1", request)
        .andExpect(status().isOk())
        .andExpect(header().string("Idempotency-Replayed", "true"))
        .andExpect(jsonPath("$.id").value(order.path("id").asString()));
    assertThat(orderCount()).isEqualTo(1);
  }

  @Test
  void idempotencyIgnoresSelectionOrderAndRejectsChangedIntentWithoutTouchingNewCartItems()
      throws Exception {
    var cart = add(1, "微辣", 0);
    cart = add(1, "不辣", cart.version());
    var ids = cart.items().stream().map(CartService.ItemView::id).toList();
    var request = command(cart.version(), ids);
    var original = body(submit(token, "same-key", request).andExpect(status().isCreated()));
    var fresh = add(1, "微辣", carts.get(customer).version());
    var reversed = new ArrayList<>(ids);
    java.util.Collections.reverse(reversed);
    submit(token, "same-key", command(cart.version(), reversed))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(original.path("id").asString()));
    submit(token, "same-key", command(fresh.version(), List.of(fresh.items().getFirst().id())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ORDER_IDEMPOTENCY_CONFLICT"));
    assertThat(carts.get(customer).version()).isEqualTo(fresh.version());
    assertThat(carts.get(customer).items()).hasSize(1);
  }

  @Test
  void concurrentIdenticalSubmissionsCreateOneOrderAndReplayTheOther() throws Exception {
    var cart = add(1, "微辣", 0);
    var request = command(cart.version(), List.of(cart.items().getFirst().id()));
    var gate = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first =
          executor.submit(
              () -> {
                gate.await();
                return submit(token, "concurrent", request).andReturn();
              });
      var second =
          executor.submit(
              () -> {
                gate.await();
                return submit(token, "concurrent", request).andReturn();
              });
      gate.countDown();
      var one = first.get(15, TimeUnit.SECONDS);
      var two = second.get(15, TimeUnit.SECONDS);
      assertThat(List.of(one.getResponse().getStatus(), two.getResponse().getStatus()))
          .containsExactlyInAnyOrder(201, 200);
      assertThat(json.readTree(one.getResponse().getContentAsString()).path("id"))
          .isEqualTo(json.readTree(two.getResponse().getContentAsString()).path("id"));
    }
    assertThat(orderCount()).isEqualTo(1);
    assertThat(carts.get(customer).items()).isEmpty();
  }

  @Test
  void differentKeysCannotSettleTheSameCartVersionTwice() throws Exception {
    var cart = add(1, "微辣", 0);
    var request = command(cart.version(), List.of(cart.items().getFirst().id()));
    var gate = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first =
          executor.submit(
              () -> {
                gate.await();
                return submit(token, "first", request).andReturn().getResponse().getStatus();
              });
      var second =
          executor.submit(
              () -> {
                gate.await();
                return submit(token, "second", request).andReturn().getResponse().getStatus();
              });
      gate.countDown();
      assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 409);
    }
    assertThat(orderCount()).isEqualTo(1);
  }

  @Test
  void failedTransactionRollsBackOrderIdempotencyAndCartSettlementTogether() {
    var cart = add(1, "微辣", 0);
    var request = command(cart.version(), List.of(cart.items().getFirst().id()));
    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    status -> {
                      orders.submit(customer, "rollback", request);
                      throw new IllegalStateException("模拟提交失败");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(orderCount()).isZero();
    assertThat(carts.get(customer).version()).isEqualTo(cart.version());
    assertThat(carts.get(customer).items()).hasSize(1);
    assertThat(orders.submit(customer, "rollback", request).replayed()).isFalse();
    assertThat(orderCount()).isEqualTo(1);
  }

  @Test
  void concurrentCartAdditionSurvivesAndAbortsStaleOrderTransaction() throws Exception {
    var cart = add(1, "微辣", 0);
    var request = command(cart.version(), List.of(cart.items().getFirst().id()));
    var selected = new CountDownLatch(1);
    var resume = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              var result = invocation.callRealMethod();
              selected.countDown();
              assertThat(resume.await(10, TimeUnit.SECONDS)).isTrue();
              return result;
            })
        .when(
            org.springframework.test.util.AopTestUtils.<CartCheckout>getUltimateTargetObject(
                checkout))
        .selected(any(), anyLong(), anyList());
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var pending = executor.submit(() -> submit(token, "stale-cart", request).andReturn());
      try {
        assertThat(selected.await(10, TimeUnit.SECONDS)).isTrue();
        add(1, "不辣", cart.version());
      } finally {
        resume.countDown();
      }
      assertThat(pending.get(15, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(409);
    }
    assertThat(orderCount()).isZero();
    assertThat(carts.get(customer).items()).hasSize(2);
  }

  @Test
  void revalidationRejectsClosedShopStaleAddressForeignAddressStaleCartAndMissingLines()
      throws Exception {
    var cart = add(1, "微辣", 0);
    var ids = List.of(cart.items().getFirst().id());
    var request = command(cart.version(), ids);
    shop.changeStatus(staff, false, shop.current().version());
    submit(token, "checks", request)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ORDER_SHOP_CLOSED"));
    shop.changeStatus(staff, true, shop.current().version());
    addresses.update(customer, addressId, address("更新地址"), 0);
    submit(token, "checks", request)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CUSTOMER_VERSION_CONFLICT"));
    var foreign = addresses.create(other, address("其他顾客地址"));
    submit(token, "checks", new OrderService.SubmitCommand(foreign.id(), 0, cart.version(), ids))
        .andExpect(status().isNotFound());
    submit(token, "checks", new OrderService.SubmitCommand(addressId, 1, 0, ids))
        .andExpect(status().isConflict());
    submit(
            token,
            "checks",
            new OrderService.SubmitCommand(
                addressId, 1, cart.version(), List.of(UUID.randomUUID())))
        .andExpect(status().isNotFound());
    assertThat(orderCount()).isZero();
    assertThat(carts.get(customer).version()).isEqualTo(cart.version());
    submit(token, "checks", new OrderService.SubmitCommand(addressId, 1, cart.version(), ids))
        .andExpect(status().isCreated());
  }

  @Test
  void currentCatalogAndFlavorsAreRequiredEvenWhenCartDisplayHasOldSnapshots() throws Exception {
    var cart = add(1, "微辣", 0);
    var request = command(cart.version(), List.of(cart.items().getFirst().id()));
    catalog.changeSale(staff, dish, false, catalog.get(staff, dish).version());
    submit(token, "catalog-check", request).andExpect(status().isNotFound());
    changeDish("新口味", "18.50", List.of(new FlavorGroup("辣度", List.of("特辣"), true)));
    submit(token, "catalog-check", request).andExpect(status().isBadRequest());
    assertThat(orderCount()).isZero();
    assertThat(carts.get(customer).items()).hasSize(1);
  }

  @Test
  void cancelledOrderRemainsImmutableAndReorderUsesCurrentPricesWithVersionProtection()
      throws Exception {
    var cart = add(2, "微辣", 0);
    var request = command(cart.version(), List.of(cart.items().getFirst().id()));
    var order = orders.submit(customer, "cancel", request).order();
    mvc.perform(
            auth(post("/api/v1/orders/{id}/cancellation", order.id()), token)
                .contentType("application/json")
                .content("{\"version\":1}"))
        .andExpect(status().isConflict());
    mvc.perform(
            auth(post("/api/v1/orders/{id}/cancellation", order.id()), token)
                .contentType("application/json")
                .content("{\"version\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.cancelledAt").isNotEmpty());
    assertThat(carts.get(customer).items()).isEmpty();
    mvc.perform(
            auth(post("/api/v1/orders/{id}/cancellation", order.id()), token)
                .contentType("application/json")
                .content("{\"version\":1}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ORDER_STATE_CONFLICT"));
    changeDish("新价格", "23.00", flavors());
    var before = add(1, "微辣", carts.get(customer).version());
    var reorder = json.writeValueAsString(Map.of("version", 1, "cartVersion", before.version()));
    mvc.perform(
            auth(post("/api/v1/orders/{id}/reorder", order.id()), token)
                .contentType("application/json")
                .content(reorder))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cartVersion").value(before.version() + 1));
    mvc.perform(
            auth(post("/api/v1/orders/{id}/reorder", order.id()), token)
                .contentType("application/json")
                .content(reorder))
        .andExpect(status().isConflict());
    assertThat(carts.get(customer).items().getFirst().quantity()).isEqualTo(3);
    assertThat(carts.get(customer).estimatedTotal()).isEqualByComparingTo("69.00");
    submit(token, "cancel", request)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.total").value(37.00));
    assertThat(orderCount()).isEqualTo(1);
  }

  @Test
  void reorderIsAtomicWhenAnyLineIsInvalidOrMergedQuantityExceedsLimit() {
    var cart = add(2, "微辣", 0);
    var order =
        orders
            .submit(
                customer, "reorder", command(cart.version(), List.of(cart.items().getFirst().id())))
            .order();
    var before = add(98, "微辣", carts.get(customer).version());
    assertThatThrownBy(() -> orders.reorder(customer, order.id(), 0, before.version()))
        .isInstanceOf(RuntimeException.class);
    assertThat(carts.get(customer).version()).isEqualTo(before.version());
    assertThat(carts.get(customer).items().getFirst().quantity()).isEqualTo(98);
    catalog.changeSale(staff, dish, false, catalog.get(staff, dish).version());
    assertThatThrownBy(() -> orders.reorder(customer, order.id(), 0, before.version()))
        .isInstanceOf(RuntimeException.class);
    assertThat(carts.get(customer).version()).isEqualTo(before.version());
  }

  @Test
  void orderResourcesAreOwnedAndHistoryIsBoundedAndContainsNoAddress() throws Exception {
    var cart = add(1, "微辣", 0);
    var order =
        orders
            .submit(
                customer, "owned", command(cart.version(), List.of(cart.items().getFirst().id())))
            .order();
    for (String credentials : List.of(otherToken, token)) {
      int expected = credentials.equals(otherToken) ? 404 : 200;
      mvc.perform(auth(get("/api/v1/orders/{id}", order.id()), credentials))
          .andExpect(status().is(expected));
    }
    mvc.perform(
            auth(post("/api/v1/orders/{id}/cancellation", order.id()), otherToken)
                .contentType("application/json")
                .content("{\"version\":0}"))
        .andExpect(status().isNotFound());
    mvc.perform(
            auth(post("/api/v1/orders/{id}/reorder", order.id()), otherToken)
                .contentType("application/json")
                .content("{\"version\":0,\"cartVersion\":0}"))
        .andExpect(status().isNotFound());
    mvc.perform(auth(get("/api/v1/orders"), otherToken)).andExpect(jsonPath("$.items").isEmpty());
    mvc.perform(auth(get("/api/v1/orders"), token))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].address").doesNotExist());
    mvc.perform(auth(get("/api/v1/orders").param("size", "51"), token))
        .andExpect(status().isBadRequest());
    mvc.perform(auth(get("/api/v1/orders").param("page", "1"), token))
        .andExpect(jsonPath("$.items").isEmpty());
    TestDatabase.awaitPublications(jdbc);
    var statistics =
        entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
    boolean enabled = statistics.isStatisticsEnabled();
    try {
      statistics.setStatisticsEnabled(true);
      statistics.clear();
      assertThat(orders.history(customer, 0, 20).items()).hasSize(1);
      // 只加载授权所需顾客实体，订单摘要使用标量投影，不能载入地址或逐条查询明细。
      assertThat(statistics.getEntityLoadCount()).isEqualTo(1);
      assertThat(statistics.getCollectionLoadCount()).isZero();
      assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
    } finally {
      statistics.setStatisticsEnabled(enabled);
    }
    mvc.perform(auth(get("/api/v1/orders"), staffToken)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
  }

  @Test
  void requestRejectsMissingKeyVersionDuplicateItemsAndClientPrices() throws Exception {
    var cart = add(1, "微辣", 0);
    var request = command(cart.version(), List.of(cart.items().getFirst().id()));
    mvc.perform(
            auth(post("/api/v1/orders"), token)
                .contentType("application/json")
                .content(json.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
    for (var invalid :
        List.of(
            Map.of(
                "addressId",
                addressId,
                "addressVersion",
                0,
                "cartVersion",
                cart.version(),
                "itemIds",
                request.itemIds(),
                "total",
                1),
            Map.of("addressId", addressId, "addressVersion", 0, "itemIds", request.itemIds()),
            Map.of(
                "addressId",
                addressId,
                "addressVersion",
                0,
                "cartVersion",
                cart.version(),
                "itemIds",
                List.of()),
            Map.of(
                "addressId",
                addressId,
                "addressVersion",
                0,
                "cartVersion",
                cart.version(),
                "itemIds",
                List.of(request.itemIds().getFirst(), request.itemIds().getFirst())))) {
      submit(token, "invalid", invalid).andExpect(status().isBadRequest());
    }
    submit(token, "bad key", request).andExpect(status().isBadRequest());
    assertThat(orderCount()).isZero();
    assertThat(carts.get(customer).version()).isEqualTo(cart.version());
  }

  @Test
  void mealOrderKeepsCompositionAndReorderRejectsUnavailableMeal() throws Exception {
    var mealCategory = catalog.createCategory(staff, ProductKind.SET_MEAL, "套餐", 0).id();
    var meal =
        catalog.createProduct(
            staff,
            ProductKind.SET_MEAL,
            mealCategory,
            "双人套餐",
            "",
            new Money(new BigDecimal("30.00")),
            null,
            List.of(),
            List.of(new MealComponent(dish, 2, Map.of("辣度", "微辣"))));
    catalog.changeSale(staff, meal.id(), true, 0);
    var cart = carts.add(customer, meal.id(), 1, Map.of(), 0);
    var order =
        orders
            .submit(
                customer, "meal", command(cart.version(), List.of(cart.items().getFirst().id())))
            .order();
    assertThat(order.total()).isEqualByComparingTo("30.00");
    assertThat(order.items().getFirst().components().getFirst().name()).isEqualTo("面条");
    assertThat(order.items().getFirst().components().getFirst().quantity()).isEqualTo(2);
    catalog.changeSale(staff, meal.id(), false, 1);
    catalog.deleteProduct(staff, meal.id(), 2);
    mvc.perform(auth(get("/api/v1/orders/{id}", order.id()), token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].components[0].selections.辣度").value("微辣"));
    assertThatThrownBy(() -> orders.reorder(customer, order.id(), 0, carts.get(customer).version()))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void laterInvalidReorderLineCannotLeaveEarlierValidLineInCart() {
    var cart = add(1, "微辣", 0);
    cart = add(1, "不辣", cart.version());
    var order =
        orders
            .submit(
                customer,
                "all-lines",
                command(
                    cart.version(), cart.items().stream().map(CartService.ItemView::id).toList()))
            .order();
    changeDish("只有微辣", "22.00", List.of(new FlavorGroup("辣度", List.of("微辣"), true)));
    long version = carts.get(customer).version();
    assertThatThrownBy(() -> orders.reorder(customer, order.id(), 0, version))
        .isInstanceOf(com.hanserwei.hanmenu.catalog.domain.CatalogException.class);
    assertThat(carts.get(customer).items()).isEmpty();
    assertThat(carts.get(customer).version()).isEqualTo(version);
    assertThat(orders.detail(customer, order.id()).items()).hasSize(2);
  }

  @Test
  void idempotencyKeysAreCustomerScopedAndRevokedIdentityCannotReplay() throws Exception {
    var cart = add(1, "微辣", 0);
    var firstRequest = command(cart.version(), List.of(cart.items().getFirst().id()));
    var first = orders.submit(customer, "shared-key", firstRequest).order();
    var otherAddress = addresses.create(other, address("其他地址"));
    var otherCart = carts.add(other, dish, 1, Map.of("辣度", "微辣"), 0);
    var second =
        orders
            .submit(
                other,
                "shared-key",
                new OrderService.SubmitCommand(
                    otherAddress.id(),
                    0,
                    otherCart.version(),
                    List.of(otherCart.items().getFirst().id())))
            .order();
    assertThat(second.id()).isNotEqualTo(first.id());
    authentication.changePassword(
        customer, PASSWORD, new CustomerPassword("Replacement-password-2026"));
    submit(token, "shared-key", firstRequest).andExpect(status().isUnauthorized());
    assertThatThrownBy(() -> orders.submit(customer, "shared-key", firstRequest))
        .isInstanceOf(com.hanserwei.hanmenu.customer.domain.CustomerException.class);
    assertThat(orderCount()).isEqualTo(2);
  }

  @Test
  void openApiDescribesAllOrderOperationsAndRequiredIdempotencyHeader() throws Exception {
    var document = body(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));
    var paths = document.path("paths");
    assertThat(paths.path("/api/v1/orders").has("post")).isTrue();
    assertThat(paths.path("/api/v1/orders").has("get")).isTrue();
    assertThat(paths.path("/api/v1/orders/{id}").has("get")).isTrue();
    assertThat(paths.path("/api/v1/orders/{id}/cancellation").has("post")).isTrue();
    assertThat(paths.path("/api/v1/orders/{id}/reorder").has("post")).isTrue();
    boolean requiredKey = false;
    for (var parameter : paths.path("/api/v1/orders").path("post").path("parameters")) {
      if (parameter.path("name").asString().equals("Idempotency-Key")) {
        requiredKey = parameter.path("required").asBoolean();
      }
    }
    assertThat(requiredKey).isTrue();
  }

  private CustomerAuthentication.LoginResult customer() {
    String phone =
        "+86"
            + java.util.concurrent.ThreadLocalRandom.current().nextLong(10000000000L, 99999999999L);
    authentication.register(
        phone, "测试顾客", new CustomerPassword(PASSWORD), UUID.randomUUID().toString());
    return authentication.login(phone, PASSWORD, UUID.randomUUID().toString());
  }

  private AddressBookService.AddressCommand address(String detail) {
    return new AddressBookService.AddressCommand(
        "家", "张先生", "+8613800138000", "浙江省", "杭州市", "西湖区", detail, false);
  }

  private List<FlavorGroup> flavors() {
    return List.of(new FlavorGroup("辣度", List.of("微辣", "不辣"), true));
  }

  private CartService.CartView add(int quantity, String flavor, long version) {
    return carts.add(customer, dish, quantity, Map.of("辣度", flavor), version);
  }

  private void changeDish(String name, String price, List<FlavorGroup> flavors) {
    var current = catalog.get(staff, dish);
    if (current.status().equals("ON_SALE")) {
      catalog.changeSale(staff, dish, false, current.version());
    }
    var updated =
        catalog.updateProduct(
            staff,
            dish,
            category,
            name,
            "",
            new Money(new BigDecimal(price)),
            null,
            flavors,
            List.of(),
            catalog.get(staff, dish).version());
    catalog.changeSale(staff, dish, true, updated.version());
  }

  private OrderService.SubmitCommand command(long version, List<UUID> items) {
    return new OrderService.SubmitCommand(addressId, 0, version, items);
  }

  private ResultActions submit(String credentials, String key, Object request) throws Exception {
    return mvc.perform(
        auth(post("/api/v1/orders"), credentials)
            .header("Idempotency-Key", key)
            .contentType("application/json")
            .content(json.writeValueAsString(request)));
  }

  private MockHttpServletRequestBuilder auth(
      MockHttpServletRequestBuilder request, String credentials) {
    return request.header("Authorization", "Bearer " + credentials);
  }

  private JsonNode body(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsString());
  }

  private long orderCount() {
    return jdbc.sql("SELECT count(*) FROM ordering_order").query(Long.class).single();
  }
}
