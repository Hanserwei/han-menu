package com.hanserwei.hanmenu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hanserwei.hanmenu.customer.application.CustomerAdministration;
import com.hanserwei.hanmenu.customer.application.CustomerTokenFactory;
import com.hanserwei.hanmenu.customer.domain.CustomerAccount;
import com.hanserwei.hanmenu.customer.domain.CustomerException;
import com.hanserwei.hanmenu.customer.domain.CustomerRepository;
import com.hanserwei.hanmenu.customer.domain.CustomerSessionRepository;
import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import com.hanserwei.hanmenu.identity.application.TokenFactory;
import com.hanserwei.hanmenu.identity.domain.AuditTrail;
import com.hanserwei.hanmenu.identity.domain.EmployeeAccount;
import com.hanserwei.hanmenu.identity.domain.EmployeeProfile;
import com.hanserwei.hanmenu.identity.domain.EmployeeRepository;
import com.hanserwei.hanmenu.identity.domain.IdentityException;
import com.hanserwei.hanmenu.identity.domain.SessionRepository;
import com.hanserwei.hanmenu.ordering.domain.AddressSnapshot;
import com.hanserwei.hanmenu.ordering.domain.Order;
import com.hanserwei.hanmenu.ordering.domain.OrderLine;
import com.hanserwei.hanmenu.ordering.domain.OrderRepository;
import com.hanserwei.hanmenu.payment.domain.Payment;
import com.hanserwei.hanmenu.payment.domain.PaymentGateway;
import com.hanserwei.hanmenu.payment.domain.PaymentRepository;
import com.hanserwei.hanmenu.payment.domain.Refund;
import com.hanserwei.hanmenu.support.TestDatabase;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 前端实施前的后台接口验收，真实数据库验证组合查询、身份边界及版本竞争. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
@TestExecutionListeners(
    listeners = {TestDatabase.Cleanup.class, TestDatabase.RedisCleanup.class},
    mergeMode = MergeMode.MERGE_WITH_DEFAULTS)
class ManagementCapabilitiesIt {
  private static final TestDatabase DATABASE = new TestDatabase(ManagementCapabilitiesIt.class);
  private static final Instant NOW = Instant.parse("2026-09-19T00:00:00Z");
  private static final BigDecimal AMOUNT = new BigDecimal("18.50");
  @Autowired private MockMvc mvc;
  @Autowired private JsonMapper json;
  @Autowired private JdbcClient jdbc;
  @Autowired private TransactionTemplate transactions;
  @Autowired private EmployeeRepository employees;
  @Autowired private SessionRepository staffSessions;
  @Autowired private TokenFactory staffTokens;
  @Autowired private CustomerRepository customers;
  @Autowired private CustomerSessionRepository customerSessions;
  @Autowired private CustomerTokenFactory customerTokens;
  @Autowired private CustomerAdministration administration;
  @Autowired private OrderRepository orders;
  @Autowired private PaymentRepository payments;
  @Autowired private AuditTrail audit;
  @MockitoBean private Clock clock;
  @MockitoBean private PaymentGateway gateway;
  private UUID customerId;
  private UUID otherCustomerId;
  private UUID orderId;
  private UUID secondOrderId;
  private UUID paymentId;
  private UUID refundId;
  private StaffIdentity admin;
  private StaffIdentity staff;
  private String adminToken;
  private String staffToken;
  private String customerToken;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    DATABASE.configure(registry);
  }

  /** 测试事实写入独立 schema，所有 HTTP 请求仍通过真实员工或顾客认证链. */
  @BeforeEach
  void fixtures() {
    when(clock.instant()).thenReturn(NOW);
    jdbc.sql(
            "TRUNCATE payment_refund, payment_intent, ordering_line, ordering_order,"
                + " customer_session, customer_account, identity_session, identity_employee,"
                + " identity_audit CASCADE")
        .update();
    var administrator =
        EmployeeAccount.create(
            UUID.randomUUID(),
            new EmployeeProfile("admin", "管理员", ""),
            "unused-hash",
            EmployeeAccount.Role.ADMIN,
            NOW);
    var employee =
        EmployeeAccount.create(
            UUID.randomUUID(),
            new EmployeeProfile("staff", "普通员工", ""),
            "unused-hash",
            EmployeeAccount.Role.STAFF,
            NOW);
    employees.add(administrator);
    employees.add(employee);
    admin = new StaffIdentity(administrator.id(), "admin", "管理员", "ADMIN", 0);
    staff = new StaffIdentity(employee.id(), "staff", "普通员工", "STAFF", 0);
    adminToken = staffSession(admin);
    staffToken = staffSession(staff);
    var customer =
        CustomerAccount.create(
            UUID.randomUUID(), "13800138001", "顾客%_!甲", "never-expose-hash", NOW);
    var other =
        CustomerAccount.create(
            UUID.randomUUID(), "13800138002", "顾客乙", "never-expose-hash", NOW.plusSeconds(1));
    customerId = customer.id();
    otherCustomerId = other.id();
    customers.add(customer);
    customers.add(other);
    customerToken = customerTokens.newToken();
    customerSessions.add(
        customerTokens.digest(customerToken), customerId, 0, NOW.plusSeconds(3600));
    var first = order(customerId, "13800138009", NOW);
    var second = order(otherCustomerId, "13800138008", NOW.plusSeconds(1));
    orderId = first.id();
    secondOrderId = second.id();
    transactions.executeWithoutResult(
        tx -> {
          orders.add(first);
          orders.add(second);
          var payment =
              Payment.create(
                  orderId,
                  customerId,
                  AMOUNT,
                  "private-idempotency-key",
                  "private-fingerprint",
                  NOW,
                  NOW.plusSeconds(900));
          payment.observe(
              new PaymentGateway.TradeResult(
                  PaymentGateway.State.SUCCEEDED, "sandbox-trade", AMOUNT, NOW.plusSeconds(10)),
              NOW.plusSeconds(10));
          payments.add(payment);
          paymentId = payment.id();
          var refund = Refund.create(payment, NOW.plusSeconds(20));
          payments.addRefund(refund);
          refundId = refund.id();
          var pending =
              Payment.create(
                  secondOrderId,
                  otherCustomerId,
                  AMOUNT,
                  "other-key",
                  "fingerprint",
                  NOW.plusSeconds(1),
                  NOW.plusSeconds(900));
          payments.add(pending);
        });
    audit.record(AuditTrail.Action.LOGIN, admin.employeeId(), admin.employeeId(), true);
    audit.record(AuditTrail.Action.LOGIN, staff.employeeId(), staff.employeeId(), false);
  }

  /** 顾客列表、资金查询与安全审计只允许管理员，订单作业仍允许普通员工. */
  @Test
  void securityChainsAndRolesRemainSeparated() throws Exception {
    for (String path :
        List.of(
            "/api/v1/management/customers",
            "/api/v1/management/customers/" + customerId,
            "/api/v1/management/payments",
            "/api/v1/management/payments/" + paymentId,
            "/api/v1/management/refunds",
            "/api/v1/management/refunds/" + refundId,
            "/api/v1/management/audit-events")) {
      mvc.perform(get(path)).andExpect(status().isUnauthorized());
      mvc.perform(auth(get(path), customerToken)).andExpect(status().isUnauthorized());
      mvc.perform(auth(get(path), staffToken)).andExpect(status().isForbidden());
      mvc.perform(auth(get(path), adminToken)).andExpect(status().isOk());
    }
    mvc.perform(auth(get("/api/v1/management/orders"), staffToken)).andExpect(status().isOk());
    mvc.perform(auth(get("/api/v1/payments/{id}", paymentId), adminToken))
        .andExpect(status().isUnauthorized());
    mvc.perform(auth(get("/api/v1/refunds/{id}", refundId), adminToken))
        .andExpect(status().isUnauthorized());
    for (String token : List.of(staffToken, customerToken)) {
      mvc.perform(
              auth(patch("/api/v1/management/customers/{id}/status", customerId), token)
                  .contentType("application/json")
                  .content("{\"enabled\":false,\"version\":0}"))
          .andExpect(token.equals(staffToken) ? status().isForbidden() : status().isUnauthorized());
    }
  }

  /** 订单条件按 AND 组合，手机号来源于历史地址而非顾客当前账号. */
  @Test
  void ordersUseSnapshotPhoneAndHalfOpenCreationRange() throws Exception {
    mvc.perform(
            auth(
                get("/api/v1/management/orders")
                    .param("status", "UNPAID")
                    .param("orderId", orderId.toString())
                    .param("customerId", customerId.toString())
                    .param("phone", "13800138009")
                    .param("from", NOW.toString())
                    .param("to", NOW.plusSeconds(1).toString()),
                staffToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].id").value(orderId.toString()))
        .andExpect(jsonPath("$.items[0].address").doesNotExist());
    mvc.perform(auth(get("/api/v1/management/orders").param("phone", "13800138001"), adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(
            auth(
                get("/api/v1/management/orders")
                    .param("orderId", orderId.toString())
                    .param("customerId", otherCustomerId.toString()),
                adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(
            auth(
                get("/api/v1/management/orders").param("page", "1").param("size", "1"), adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.items[0].id").value(orderId.toString()));
  }

  /** 名称中的 SQL 通配符作为普通字符匹配，查询响应不含任何凭证字段. */
  @Test
  void customersSupportLiteralNamePhoneStatusAndRegistrationFilters() throws Exception {
    var result =
        mvc.perform(
                auth(
                    get("/api/v1/management/customers")
                        .param("name", "%_!")
                        .param("phone", "13800138001")
                        .param("enabled", "true")
                        .param("from", NOW.toString())
                        .param("to", NOW.plusSeconds(1).toString()),
                    adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].id").value(customerId.toString()))
            .andExpect(jsonPath("$.items[0].phone").value("+13800138001"))
            .andReturn();
    assertThat(result.getResponse().getContentAsString())
        .doesNotContain("passwordHash", "securityVersion", "never-expose-hash");
    mvc.perform(auth(get("/api/v1/management/customers").param("enabled", "false"), adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(
            auth(
                get("/api/v1/management/customers").param("page", "1").param("size", "1"),
                adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(customerId.toString()));
  }

  /** 启停用采用版本控制，停用后旧令牌失效，再启用不会恢复旧令牌. */
  @Test
  void customerStatusRevokesSessionsAndAuditsOnlyActualChanges() throws Exception {
    mvc.perform(auth(get("/api/v1/customer/me"), customerToken)).andExpect(status().isOk());
    changeStatus(false, 0)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false))
        .andExpect(jsonPath("$.version").value(1));
    mvc.perform(auth(get("/api/v1/customer/me"), customerToken))
        .andExpect(status().isUnauthorized());
    changeStatus(true, 0)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CUSTOMER_VERSION_CONFLICT"));
    changeStatus(false, 1).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
    assertThat(statusAudits()).isEqualTo(1);
    changeStatus(true, 1).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
    mvc.perform(auth(get("/api/v1/customer/me"), customerToken))
        .andExpect(status().isUnauthorized());
    assertThat(statusAudits()).isEqualTo(2);
    assertThat(customers.findById(customerId).orElseThrow().securityVersion()).isEqualTo(2);
  }

  /** 同版本并发写只成功一次，失败者不写入第二条审计. */
  @Test
  void concurrentStatusChangesHaveOneWinner() throws Exception {
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var tasks =
          java.util.stream.IntStream.range(0, 2)
              .mapToObj(
                  ignored ->
                      pool.submit(
                          () -> {
                            start.await(5, TimeUnit.SECONDS);
                            try {
                              administration.changeStatus(admin, customerId, false, 0);
                              return "changed";
                            } catch (CustomerException failure) {
                              return failure.reason().name();
                            }
                          }))
              .toList();
      start.countDown();
      assertThat(
              List.of(
                  tasks.get(0).get(10, TimeUnit.SECONDS), tasks.get(1).get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("changed", "VERSION_CONFLICT");
    }
    assertThat(statusAudits()).isEqualTo(1);
  }

  /** 回滚同时撤销状态与审计，应用服务还会复验管理员真实角色和安全版本. */
  @Test
  void statusAndAuditRollbackTogetherAndAuthorizationIsRechecked() {
    transactions.executeWithoutResult(
        tx -> {
          administration.changeStatus(admin, customerId, false, 0);
          tx.setRollbackOnly();
        });
    assertThat(customers.findById(customerId).orElseThrow().enabled()).isTrue();
    assertThat(statusAudits()).isZero();
    var forged = new StaffIdentity(staff.employeeId(), "staff", "员工", "ADMIN", 0);
    assertThatThrownBy(() -> administration.changeStatus(forged, customerId, false, 0))
        .isInstanceOf(IdentityException.class);
    var stale = new StaffIdentity(admin.employeeId(), "admin", "管理员", "ADMIN", 1);
    assertThatThrownBy(() -> administration.get(stale, customerId))
        .isInstanceOf(IdentityException.class);
  }

  /** 支付明细以本模块业务引用查询，读取不调用渠道，也不返回签名或幂等凭证. */
  @Test
  void paymentQueriesAreFilteredReadOnlyAndSecretFree() throws Exception {
    var result =
        mvc.perform(
                auth(
                    get("/api/v1/management/payments")
                        .param("orderId", orderId.toString())
                        .param("customerId", customerId.toString())
                        .param("paymentId", paymentId.toString())
                        .param("status", "SUCCEEDED")
                        .param("from", NOW.toString())
                        .param("to", NOW.plusSeconds(1).toString()),
                    adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].orderId").value(orderId.toString()))
            .andExpect(jsonPath("$.items[0].amount").value(18.50))
            .andExpect(jsonPath("$.items[0].currency").value("CNY"))
            .andReturn();
    assertThat(result.getResponse().getContentAsString())
        .doesNotContain("idempotencyKey", "fingerprint", "private-", "signedParameters");
    mvc.perform(auth(get("/api/v1/management/payments/{id}", paymentId), adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tradeNo").value("sandbox-trade"))
        .andExpect(jsonPath("$.version").value(0));
    mvc.perform(auth(get("/api/v1/management/payments").param("status", "PENDING"), adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].orderId").value(secondOrderId.toString()));
    mvc.perform(
            auth(
                get("/api/v1/management/payments").param("page", "1").param("size", "1"),
                adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(paymentId.toString()));
    verifyNoInteractions(gateway);
  }

  /** 退款检索使用退款创建时刻，受理中不会伪装为成功. */
  @Test
  void refundQueriesPreservePendingStateAndTimeBoundaries() throws Exception {
    var path = "/api/v1/management/refunds";
    mvc.perform(
            auth(
                get(path)
                    .param("orderId", orderId.toString())
                    .param("paymentId", paymentId.toString())
                    .param("customerId", customerId.toString())
                    .param("status", "PENDING")
                    .param("from", NOW.plusSeconds(20).toString())
                    .param("to", NOW.plusSeconds(21).toString()),
                adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].id").value(refundId.toString()))
        .andExpect(jsonPath("$.items[0].confirmedAt").isEmpty());
    mvc.perform(auth(get(path).param("to", NOW.plusSeconds(20).toString()), adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(auth(get(path).param("status", "SUCCEEDED"), adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(auth(get(path + "/{id}", refundId), adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paymentId").value(paymentId.toString()));
    verifyNoInteractions(gateway);
  }

  /** 安全审计条件按事件、操作者、目标和结果组合，发生时间有明确边界. */
  @Test
  void auditQueriesFilterFixedEventsWithoutPersonalData() throws Exception {
    var result =
        mvc.perform(
                auth(
                    get("/api/v1/management/audit-events")
                        .param("action", "LOGIN")
                        .param("actorId", admin.employeeId().toString())
                        .param("subjectId", admin.employeeId().toString())
                        .param("successful", "true")
                        .param("from", NOW.toString())
                        .param("to", NOW.plusSeconds(1).toString()),
                    adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].action").value("LOGIN"))
            .andReturn();
    assertThat(result.getResponse().getContentAsString())
        .doesNotContain("phone", "password", "token", "displayName");
    mvc.perform(
            auth(get("/api/v1/management/audit-events").param("successful", "false"), adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].actorId").value(staff.employeeId().toString()));
    mvc.perform(
            auth(get("/api/v1/management/audit-events").param("to", NOW.toString()), adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    var first = read(auth(get("/api/v1/management/audit-events").param("size", "1"), adminToken));
    var second =
        read(
            auth(
                get("/api/v1/management/audit-events").param("size", "1").param("page", "1"),
                adminToken));
    assertThat(first.path("items").get(0).path("id").asString())
        .isNotEqualTo(second.path("items").get(0).path("id").asString());
  }

  /** 非法范围、类型与分页均返回标准问题详情，不扩大为无条件查询. */
  @Test
  void malformedFiltersAndMissingResourcesHaveDefinedErrors() throws Exception {
    for (String path : List.of("orders", "customers", "payments", "refunds", "audit-events")) {
      for (Map<String, String> params :
          List.of(
              Map.of("page", "-1"),
              Map.of("page", "10001"),
              Map.of("size", "0"),
              Map.of("size", "51"),
              Map.of("from", NOW.toString(), "to", NOW.toString()),
              Map.of("from", "invalid"))) {
        var request = get("/api/v1/management/" + path);
        params.forEach(request::param);
        mvc.perform(auth(request, adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.traceId").isNotEmpty());
      }
    }
    for (String path : List.of("orders", "customers", "payments", "refunds")) {
      mvc.perform(auth(get("/api/v1/management/" + path + "/" + UUID.randomUUID()), adminToken))
          .andExpect(status().isNotFound());
    }
    mvc.perform(auth(get("/api/v1/management/orders").param("orderId", "invalid"), adminToken))
        .andExpect(status().isBadRequest());
    mvc.perform(auth(get("/api/v1/management/orders").param("phone", "123%"), adminToken))
        .andExpect(status().isBadRequest());
    mvc.perform(auth(get("/api/v1/management/customers").param("name", " "), adminToken))
        .andExpect(status().isBadRequest());
    mvc.perform(
            auth(get("/api/v1/management/payments").param("status", "FAKE_SUCCESS"), adminToken))
        .andExpect(status().isBadRequest());
    mvc.perform(
            auth(patch("/api/v1/management/customers/{id}/status", customerId), adminToken)
                .contentType("application/json")
                .content("{\"enabled\":false}"))
        .andExpect(status().isBadRequest());
  }

  /** 新接口同时出现在 OpenAPI 中，前端无需手写隐藏的 HTTP 契约. */
  @Test
  void openApiDocumentsManagementRoutesAndOrderFilters() throws Exception {
    var root = read(get("/v3/api-docs"));
    for (String path :
        List.of(
            "/api/v1/management/customers",
            "/api/v1/management/customers/{id}/status",
            "/api/v1/management/payments",
            "/api/v1/management/refunds",
            "/api/v1/management/audit-events")) {
      assertThat(root.path("paths").has(path)).isTrue();
    }
    assertThat(
            root.path("paths")
                .path("/api/v1/notifications/stream-tickets")
                .path("post")
                .path("parameters")
                .toString())
        .doesNotContain("Authorization");
    var schemas = root.path("components").path("schemas");
    assertThat(
            schemas
                .path("History")
                .path("properties")
                .path("items")
                .path("items")
                .path("$ref")
                .asString())
        .isEqualTo("#/components/schemas/OrderSummary");
    assertThat(schemas.path("OrderSummary").path("properties").has("total")).isTrue();
    assertThat(schemas.path("OrderSummary").path("properties").has("status")).isTrue();
    assertThat(schemas.path("Summary").path("properties").has("turnover")).isTrue();
    assertThat(schemas.path("CustomerStatusChange").path("properties").has("enabled")).isTrue();
    assertThat(
            schemas
                .path("EmployeeStatusChange")
                .path("properties")
                .path("status")
                .path("enum")
                .toString())
        .contains("ACTIVE", "DISABLED")
        .doesNotContain("OPEN");
    assertThat(
            schemas
                .path("ShopStatusChange")
                .path("properties")
                .path("status")
                .path("enum")
                .toString())
        .contains("OPEN", "CLOSED")
        .doesNotContain("ACTIVE");
    assertThat(
            schemas
                .path("CustomerPageView")
                .path("properties")
                .path("items")
                .path("items")
                .path("$ref")
                .asString())
        .isEqualTo("#/components/schemas/ManagedCustomerView");
    assertThat(
            schemas
                .path("AuditPageView")
                .path("properties")
                .path("items")
                .path("items")
                .path("$ref")
                .asString())
        .isEqualTo("#/components/schemas/AuditEntryView");
    assertThat(schemas.path("ManagedCustomerView").path("properties").has("phone")).isTrue();
    assertThat(schemas.path("ManagedCustomerView").path("properties").has("enabled")).isTrue();
    assertThat(schemas.path("CustomerView").path("properties").has("enabled")).isFalse();
    assertThat(schemas.path("AuditEntryView").path("properties").has("action")).isTrue();
    assertThat(schemas.path("ManagedPaymentView").path("properties").has("orderId")).isTrue();
    assertThat(schemas.path("ManagedRefundView").path("properties").has("customerId")).isTrue();
    assertThat(
            root.path("paths")
                .path("/api/v1/management/orders")
                .path("get")
                .path("parameters")
                .toString())
        .contains("orderId", "customerId", "phone", "from", "to", "status", "page", "size");
  }

  private String staffSession(StaffIdentity identity) {
    String token = staffTokens.newToken();
    staffSessions.add(staffTokens.digest(token), identity.employeeId(), 0, NOW.plusSeconds(3600));
    return token;
  }

  private Order order(UUID owner, String phone, Instant created) {
    return Order.submit(
        owner,
        UUID.randomUUID().toString(),
        "fingerprint",
        new AddressSnapshot(UUID.randomUUID(), 0, "收货人", "+" + phone, "浙江省", "杭州市", "西湖区", "历史地址"),
        List.of(
            new OrderLine(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "DISH",
                "面条",
                AMOUNT,
                1,
                Map.of(),
                List.of())),
        created);
  }

  private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
    return request.header("Authorization", "Bearer " + token);
  }

  private org.springframework.test.web.servlet.ResultActions changeStatus(
      boolean enabled, long version) throws Exception {
    return mvc.perform(
        auth(patch("/api/v1/management/customers/{id}/status", customerId), adminToken)
            .contentType("application/json")
            .content("{\"enabled\":" + enabled + ",\"version\":" + version + "}"));
  }

  private long statusAudits() {
    return jdbc.sql("SELECT count(*) FROM identity_audit WHERE action='CHANGE_CUSTOMER_STATUS'")
        .query(Long.class)
        .single();
  }

  private JsonNode read(MockHttpServletRequestBuilder request) throws Exception {
    return json.readTree(
        mvc.perform(request)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }
}
