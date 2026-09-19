package com.hanserwei.hanmenu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hanserwei.hanmenu.catalog.application.CatalogAdministration;
import com.hanserwei.hanmenu.catalog.domain.Money;
import com.hanserwei.hanmenu.catalog.domain.ProductKind;
import com.hanserwei.hanmenu.customer.api.CustomerFacts;
import com.hanserwei.hanmenu.customer.api.CustomerIdentity;
import com.hanserwei.hanmenu.customer.application.CustomerTokenFactory;
import com.hanserwei.hanmenu.customer.domain.CustomerAccount;
import com.hanserwei.hanmenu.customer.domain.CustomerRepository;
import com.hanserwei.hanmenu.customer.domain.CustomerSessionRepository;
import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import com.hanserwei.hanmenu.identity.application.TokenFactory;
import com.hanserwei.hanmenu.identity.domain.EmployeeAccount;
import com.hanserwei.hanmenu.identity.domain.EmployeeProfile;
import com.hanserwei.hanmenu.identity.domain.EmployeeRepository;
import com.hanserwei.hanmenu.identity.domain.SessionRepository;
import com.hanserwei.hanmenu.notification.application.NotificationDispatcher;
import com.hanserwei.hanmenu.notification.application.NotificationService;
import com.hanserwei.hanmenu.notification.application.StreamTicketService;
import com.hanserwei.hanmenu.notification.domain.Notice;
import com.hanserwei.hanmenu.notification.domain.NotificationPush;
import com.hanserwei.hanmenu.notification.web.NotificationSocket;
import com.hanserwei.hanmenu.ordering.api.OrderFacts;
import com.hanserwei.hanmenu.ordering.application.OrderLifecycleService;
import com.hanserwei.hanmenu.ordering.application.OrderPaymentService;
import com.hanserwei.hanmenu.ordering.application.OrderService;
import com.hanserwei.hanmenu.ordering.domain.AddressSnapshot;
import com.hanserwei.hanmenu.ordering.domain.Order;
import com.hanserwei.hanmenu.ordering.domain.OrderLine;
import com.hanserwei.hanmenu.ordering.domain.OrderRepository;
import com.hanserwei.hanmenu.payment.application.PaymentTransactions;
import com.hanserwei.hanmenu.payment.domain.PaymentGateway;
import com.hanserwei.hanmenu.reporting.application.ProjectionMaintenance;
import com.hanserwei.hanmenu.reporting.application.ReportProjector;
import com.hanserwei.hanmenu.reporting.application.ReportQueries;
import com.hanserwei.hanmenu.reporting.application.ReportingService;
import com.hanserwei.hanmenu.shop.application.ShopService;
import com.hanserwei.hanmenu.support.TestDatabase;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestExecutionListeners.MergeMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** P6 真实数据库、HTTP 和 WebSocket 验收，验证补查、撤销、乱序及原子重建. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
@TestExecutionListeners(
    listeners = {TestDatabase.Cleanup.class, TestDatabase.RedisCleanup.class},
    mergeMode = MergeMode.MERGE_WITH_DEFAULTS)
class NotificationAndReportingIt {
  private static final TestDatabase DATABASE = new TestDatabase(NotificationAndReportingIt.class);
  private static final Instant NOW = Instant.parse("2026-09-18T15:50:00Z");
  private static final LocalDate FROM = LocalDate.parse("2026-09-18");
  private static final LocalDate TO = LocalDate.parse("2026-09-20");
  @Autowired private MockMvc mvc;
  @Autowired private JdbcClient jdbc;
  @Autowired private jakarta.persistence.EntityManagerFactory entityManagerFactory;
  @Autowired private JsonMapper json;
  @Autowired private CustomerRepository customers;
  @Autowired private CustomerSessionRepository customerSessions;
  @Autowired private CustomerTokenFactory customerTokens;
  @Autowired private EmployeeRepository employees;
  @Autowired private SessionRepository employeeSessions;
  @Autowired private TokenFactory employeeTokens;
  @Autowired private OrderRepository orderRepository;
  @Autowired private OrderService orders;
  @Autowired private OrderLifecycleService lifecycle;
  @Autowired private OrderPaymentService orderPayments;
  @Autowired private PaymentTransactions payments;
  @Autowired private NotificationService notifications;
  @Autowired private NotificationDispatcher dispatcher;
  @Autowired private StreamTicketService tickets;
  @Autowired private ReportProjector projector;
  @Autowired private ProjectionMaintenance maintenance;
  @Autowired private ReportQueries queries;
  @Autowired private ReportingService reports;
  @Autowired private TransactionTemplate transactions;
  @Autowired private CatalogAdministration catalog;
  @Autowired private ShopService shop;
  @MockitoBean private PaymentGateway gateway;
  @MockitoBean private Clock clock;
  @MockitoSpyBean private NotificationSocket socket;
  @MockitoSpyBean private OrderFacts sourceOrders;
  @MockitoSpyBean private CustomerFacts sourceCustomers;

  @Value("${local.server.port}")
  private int port;

  private final AtomicReference<Instant> time = new AtomicReference<>(NOW);
  private final List<WebSocket> sockets = new ArrayList<>();
  private HttpClient client;
  private StaffIdentity admin;
  private StaffIdentity staff;
  private CustomerIdentity customer;
  private CustomerIdentity other;
  private String adminToken;
  private String staffToken;
  private String customerToken;
  private String otherToken;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    DATABASE.configure(registry);
  }

  /** 所有账号、投影和消息均只存在于当前随机测试 schema. */
  @BeforeEach
  void fixtures() {
    time.set(NOW);
    when(clock.instant()).thenAnswer(ignored -> time.get());
    jdbc.sql(
            "TRUNCATE event_publication, notification_attempt, notification_notice,"
                + " notification_receipt, notification_ticket, reporting_line,"
                + " reporting_order, reporting_product, reporting_customer,"
                + " reporting_receipt, reporting_refund, payment_refund, payment_intent,"
                + " ordering_line, ordering_order, cart_item, cart, customer_address,"
                + " customer_session, customer_account, identity_session, identity_audit,"
                + " identity_employee, catalog_meal_component, catalog_product,"
                + " catalog_category CASCADE")
        .update();
    jdbc.sql("UPDATE notification_feed SET sequence = 0 WHERE id = 1").update();
    jdbc.sql(
            "UPDATE reporting_projection SET version = 0, generation = 0,"
                + " revision = 0, initialized = false,"
                + " updated_at = '1970-01-01T00:00:00Z', rebuilt_at = NULL WHERE id = 1")
        .update();
    socket.prune();
    admin = employee("admin", EmployeeAccount.Role.ADMIN);
    staff = employee("staff", EmployeeAccount.Role.STAFF);
    adminToken = staffToken(admin);
    staffToken = staffToken(staff);
    customer = customer("+8613800138001");
    other = customer("+8613800138002");
    customerToken = customerToken(customer);
    otherToken = customerToken(other);
    if (shop.current().status().equals("OPEN")) {
      shop.changeStatus(admin, false, shop.current().version());
    }
    when(gateway.appParameters(any())).thenReturn("test-signed-parameters");
    maintenance.initialize();
    client = HttpClient.newHttpClient();
  }

  /** 等待可靠消费者结束后才重置测试时钟和替身，关闭所有真实测试连接. */
  @AfterEach
  void cleanup() throws Exception {
    for (var connection : sockets) {
      connection.abort();
    }
    sockets.clear();
    socket.prune();
    if (client != null) {
      client.close();
    }
    TestDatabase.awaitPublications(jdbc);
  }

  @Test
  void paidOrderAndVersionedRemindersProduceDurableDeduplicatedNotifications() throws Exception {
    UUID orderId = seedOrder();
    mvc.perform(
            post("/api/v1/orders/{id}/reminders", orderId)
                .header("Authorization", "Bearer " + customerToken)
                .contentType("application/json")
                .content("{\"version\":0}"))
        .andExpect(status().isConflict());
    UUID paymentId = orderPayments.create(customer, orderId, "payment", 0).payment().id();
    payments.observe(paymentId, success(new BigDecimal("18.50"), "202609180000000000001"));
    TestDatabase.awaitPublications(jdbc);
    var feed = notifications.feed(staff, 0, 50);
    assertThat(feed.items()).hasSize(1);
    assertThat(feed.items().getFirst().type()).isEqualTo("NEW_ORDER");
    long version = orders.detail(customer, orderId).version();
    mvc.perform(
            post("/api/v1/orders/{id}/reminders", orderId)
                .header("Authorization", "Bearer " + otherToken)
                .contentType("application/json")
                .content("{\"version\":" + version + "}"))
        .andExpect(status().isNotFound());
    lifecycle.remind(customer, orderId, version);
    assertThatThrownBy(() -> lifecycle.remind(customer, orderId, version))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(
            () -> lifecycle.remind(customer, orderId, orders.detail(customer, orderId).version()))
        .isInstanceOf(RuntimeException.class);
    TestDatabase.awaitPublications(jdbc);
    feed = notifications.feed(staff, 0, 50);
    assertThat(feed.items()).hasSize(2);
    var reminder = feed.items().getLast();
    notifications.append(reminder.id(), orderId, Notice.Kind.ORDER_REMINDER, reminder.occurredAt());
    assertThat(notifications.feed(staff, 0, 50).items()).hasSize(2);
    time.set(NOW.plusSeconds(60));
    lifecycle.remind(customer, orderId, orders.detail(customer, orderId).version());
    TestDatabase.awaitPublications(jdbc);
    assertThat(notifications.feed(staff, 0, 50).items()).hasSize(3);
    assertThat(notifications.receipt(staff).sequence()).isZero();
  }

  @Test
  void cursorPaginationAndReceiptsAreCommittedMonotoneAndPerEmployee() {
    UUID first = append();
    UUID second = append();
    var page = notifications.feed(staff, 0, 1);
    assertThat(page.items().getFirst().id()).isEqualTo(first);
    assertThat(page.hasMore()).isTrue();
    assertThat(notifications.feed(staff, page.nextCursor(), 1).items().getFirst().id())
        .isEqualTo(second);
    var receipt = notifications.acknowledge(staff, 1, 0);
    assertThat(receipt.version()).isEqualTo(1);
    assertThat(notifications.receipt(admin).sequence()).isZero();
    assertThatThrownBy(() -> notifications.acknowledge(staff, 2, 0))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> notifications.acknowledge(staff, 0, 1))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> notifications.acknowledge(staff, 3, 1))
        .isInstanceOf(RuntimeException.class);
    assertThat(notifications.acknowledge(staff, 2, 1).sequence()).isEqualTo(2);
    transactions.executeWithoutResult(
        status -> {
          append();
          status.setRollbackOnly();
        });
    assertThat(notifications.feed(staff, 2, 50).items()).isEmpty();
    assertThat(notifications.feed(staff, 0, 50).nextCursor()).isEqualTo(2);
  }

  @Test
  void commitOrderedCursorCannotSkipAnEarlierUncommittedMessage() throws Exception {
    var firstWritten = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first =
          executor.submit(
              () ->
                  transactions.execute(
                      status -> {
                        UUID id = append();
                        firstWritten.countDown();
                        try {
                          assertThat(release.await(8, TimeUnit.SECONDS)).isTrue();
                        } catch (InterruptedException exception) {
                          throw new IllegalStateException(exception);
                        }
                        return id;
                      }));
      assertThat(firstWritten.await(5, TimeUnit.SECONDS)).isTrue();
      var second = executor.submit(this::append);
      try {
        assertThat(notifications.feed(staff, 0, 50).items()).isEmpty();
      } finally {
        release.countDown();
      }
      UUID one = first.get(8, TimeUnit.SECONDS);
      UUID two = second.get(8, TimeUnit.SECONDS);
      assertThat(
              notifications.feed(staff, 0, 50).items().stream()
                  .map(NotificationService.NoticeView::id))
          .containsExactly(one, two);
    }
  }

  @Test
  void failedDeliveryIsTrackedBoundedAndCanBeRetriedByAdministrator() {
    UUID id = append();
    for (int attempt = 0; attempt < 5; attempt++) {
      dispatcher.deliver(id);
      time.set(time.get().plusSeconds(60));
    }
    var notice = notifications.feed(staff, 0, 10).items().getFirst();
    assertThat(notice.deliveryStatus()).isEqualTo("EXHAUSTED");
    assertThat(notifications.attempts(admin, id))
        .hasSize(5)
        .allMatch(value -> value.status().equals("FAILED"));
    assertThatThrownBy(() -> notifications.redeliver(staff, id, notice.version()))
        .isInstanceOf(RuntimeException.class);
    notifications.redeliver(admin, id, notice.version());
    doAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return new NotificationPush.Outcome(1, 0);
            })
        .when(socket)
        .send(any());
    dispatcher.deliver(id);
    assertThat(notifications.feed(staff, 0, 10).items().getFirst().deliveryStatus())
        .isEqualTo("DELIVERED");
    assertThat(notifications.attempts(admin, id)).hasSize(6);
    assertThat(notifications.receipt(staff).sequence()).isZero();
  }

  @Test
  void realWebSocketUsesSingleUseTicketAndChecksLogoutBeforeSending() throws Exception {
    var issued = tickets.issue(staffToken);
    var messages = new Messages();
    WebSocket connection = connect(issued.ticket(), messages, null);
    assertThat(connection.getSubprotocol()).isEqualTo("han-menu.notifications.v1");
    assertThat(messages.next()).contains("READY");
    UUID id = append();
    dispatcher.deliver(id);
    var frame = json.readTree(messages.next());
    assertThat(frame.path("id").asString()).isEqualTo(id.toString());
    assertThat(frame.path("sequence").asLong()).isEqualTo(1);
    assertThat(notifications.receipt(staff).sequence()).isZero();
    assertHandshakeDenied(issued.ticket(), null, 401);
    employeeSessions.revoke(employeeTokens.digest(staffToken));
    socket.prune();
    assertThat(messages.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
    assertThat(tickets.toString()).doesNotContain(staffToken);
  }

  @Test
  void websocketRejectsForeignOriginsExpiredTicketsAndCustomerCredentials() throws Exception {
    var issued = tickets.issue(staffToken);
    assertHandshakeDenied(issued.ticket(), "https://evil.example", 403);
    var messages = new Messages();
    connect(issued.ticket(), messages, null);
    assertThat(messages.next()).contains("READY");
    var expired = tickets.issue(adminToken);
    time.set(NOW.plusSeconds(31));
    assertHandshakeDenied(expired.ticket(), null, 401);
    assertHandshakeDenied("hmw_" + "a".repeat(43), null, 401);
    mvc.perform(
            post("/api/v1/notifications/stream-tickets")
                .header("Authorization", "Bearer " + customerToken))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
  }

  @Test
  void disablingEmployeeClosesExistingSocketAndPreventsTicketReuse() throws Exception {
    var messages = new Messages();
    connect(tickets.issue(staffToken).ticket(), messages, null);
    messages.next();
    final var pending = tickets.issue(staffToken);
    var employee = employees.findById(staff.employeeId()).orElseThrow();
    employee.changeEnabled(false, NOW);
    employees.update(employee);
    socket.prune();
    assertThat(messages.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
    assertHandshakeDenied(pending.ticket(), null, 401);
  }

  @Test
  void reportsSeparateCompletedTurnoverCashRefundDatesAndCustomerGrowth() {
    metrics();
    var result = reports.operations(admin, FROM, TO);
    assertThat(result.days()).hasSize(3);
    assertThat(result.summary().submittedOrders()).isEqualTo(3);
    assertThat(result.summary().completedOrders()).isEqualTo(2);
    assertThat(result.summary().turnover()).isEqualByComparingTo("32.10");
    assertThat(result.summary().receivedAmount()).isEqualByComparingTo("39.60");
    assertThat(result.summary().refundedAmount()).isEqualByComparingTo("7.50");
    assertThat(result.summary().completionRatePercent()).isEqualByComparingTo("66.67");
    assertThat(result.summary().averageOrderValue()).isEqualByComparingTo("16.05");
    assertThat(result.days().getFirst().turnover()).isEqualByComparingTo("0.00");
    assertThat(result.days().get(1).turnover()).isEqualByComparingTo("32.10");
    assertThat(result.days().getLast().netReceivedAmount()).isEqualByComparingTo("-7.50");
    assertThat(result.summary().newCustomers()).isEqualTo(3);
    assertThat(reports.operations(admin, FROM.plusDays(1), TO).summary().newCustomers())
        .isEqualTo(1);
    var sales = reports.sales(admin, FROM, TO, 10).items();
    assertThat(sales).hasSize(1);
    assertThat(sales.getFirst().quantity()).isEqualTo(3);
    assertThat(sales.getFirst().name()).isEqualTo("=HYPERLINK(\"https://invalid.example\")");
    var reconciliation = reports.reconcile(admin, FROM, TO);
    assertThat(reconciliation.missingOrders()).isZero();
    assertThat(reconciliation.paymentMismatches()).isZero();
    assertThat(reconciliation.missingPayments()).isZero();
    assertThat(reconciliation.refundMismatches()).isZero();
  }

  @Test
  void outOfOrderFactsCannotRegressStatisticsAndFinancialEventsAreIdempotent() {
    UUID product = UUID.randomUUID();
    UUID payment = UUID.randomUUID();
    var complete =
        fact(
            UUID.randomUUID(),
            product,
            "面条",
            "10.05",
            2,
            "COMPLETED",
            3,
            NOW,
            NOW.plusSeconds(1),
            NOW.plusSeconds(2),
            payment,
            "NONE",
            null);
    projector.order(complete);
    assertThat(reports.reconcile(admin, FROM, FROM).ordersMissingReceipts()).isEqualTo(1);
    long revision = queries.projection(admin).revision();
    projector.order(complete);
    var older =
        new OrderFacts.Snapshot(
            complete.id(),
            complete.customerId(),
            1,
            "PAID",
            complete.total(),
            complete.createdAt(),
            complete.paidAt(),
            null,
            null,
            payment,
            "NONE",
            null,
            complete.lines());
    projector.order(older);
    assertThat(queries.projection(admin).revision()).isEqualTo(revision);
    projector.receipt(payment, complete.id(), complete.total(), complete.paidAt());
    projector.receipt(payment, complete.id(), complete.total(), complete.paidAt());
    assertThat(queries.projection(admin).receipts()).isEqualTo(1);
    assertThat(reports.operations(admin, FROM, FROM).summary().completedOrders()).isEqualTo(1);
    UUID refund = UUID.randomUUID();
    UUID absentPayment = UUID.randomUUID();
    projector.refund(refund, absentPayment, UUID.randomUUID(), new BigDecimal("1.00"), NOW);
    assertThat(reports.reconcile(admin, FROM, FROM).missingPayments()).isEqualTo(1);
  }

  @Test
  void xlsxExportMatchesJsonUsesLiteralNamesAndProtectsFinancialPermissions() throws Exception {
    metrics();
    var result =
        mvc.perform(
                get("/api/v1/reports/export")
                    .param("from", FROM.toString())
                    .param("to", TO.toString())
                    .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(result.getResponse().getContentType()).contains("spreadsheetml.sheet");
    try (var workbook =
        new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
      assertThat(workbook.getNumberOfSheets()).isEqualTo(4);
      assertThat(workbook.getSheet("经营日账").getRow(2).getCell(5).getStringCellValue())
          .isEqualTo("32.10");
      var name = workbook.getSheet("商品销量").getRow(1).getCell(2);
      assertThat(name.getCellType()).isEqualTo(CellType.STRING);
      assertThat(name.getStringCellValue()).startsWith("=HYPERLINK");
      assertThat(workbook.getSheet("商品销量").getRow(1).getCell(4).getStringCellValue())
          .isEqualTo("32.10");
    }
    mvc.perform(
            get("/api/v1/reports/operations")
                .param("from", FROM.toString())
                .param("to", TO.toString())
                .header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/reports/export")
                .param("from", FROM.toString())
                .param("to", TO.toString())
                .header("Authorization", "Bearer " + customerToken))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/v1/reports/operations")
                .param("from", FROM.toString())
                .param("to", FROM.plusDays(366).toString())
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isBadRequest());
  }

  @Test
  void workspaceUsesOperationalCountsAndLiveCatalogWithoutExposingMoney() throws Exception {
    var category = catalog.createCategory(admin, ProductKind.DISH, "菜品", 0);
    var dish =
        catalog.createProduct(
            admin,
            ProductKind.DISH,
            category.id(),
            "面条",
            "",
            new Money(new BigDecimal("18.50")),
            null,
            List.of(),
            List.of());
    catalog.changeSale(admin, dish.id(), true, 0);
    shop.revise(admin, "测试门店", "13800138000", "测试地址", shop.current().version());
    shop.changeStatus(admin, true, shop.current().version());
    var body =
        mvc.perform(get("/api/v1/workspace").header("Authorization", "Bearer " + staffToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopStatus").value("OPEN"))
            .andExpect(jsonPath("$.dishesOnSale").value(1))
            .andExpect(jsonPath("$.turnover").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).doesNotContain(customer.phone(), "收货");
  }

  @Test
  void rebuildUsesSourceApisAcrossPagesAndRepairsProjectionWithoutOldEventsRegressingIt()
      throws Exception {
    for (int i = 0; i < 205; i++) {
      seedOrder();
    }
    assertThat(queries.projection(admin).orders()).isZero();
    long version = queries.projection(admin).version();
    var result = maintenance.rebuild(admin, version);
    assertThat(result.orders()).isEqualTo(205);
    assertThat(result.generation()).isEqualTo(2);
    assertThat(reports.operations(admin, FROM, FROM).summary().submittedOrders()).isEqualTo(205);
    assertThatThrownBy(() -> maintenance.rebuild(admin, version))
        .isInstanceOf(RuntimeException.class);
    var source = sourceOrders.after(null, 1).getFirst();
    lifecycle.cancel(customer, source.id(), source.version());
    TestDatabase.awaitPublications(jdbc);
    var before = reports.operations(admin, FROM, FROM).summary().cancelledCohort();
    projector.order(source);
    assertThat(reports.operations(admin, FROM, FROM).summary().cancelledCohort()).isEqualTo(before);
  }

  @Test
  void failedRebuildRollsBackDeletionAndKeepsOldGenerationVisible() {
    metrics();
    var before = reports.operations(admin, FROM, TO);
    long generation = queries.projection(admin).generation();
    doThrow(new IllegalStateException("模拟源快照失败"))
        .when(AopTestUtils.<CustomerFacts>getUltimateTargetObject(sourceCustomers))
        .after(any(), anyInt());
    assertThatThrownBy(() -> maintenance.rebuild(admin, queries.projection(admin).version()))
        .isInstanceOf(IllegalStateException.class);
    assertThat(queries.projection(admin).generation()).isEqualTo(generation);
    assertThat(reports.operations(admin, FROM, TO).summary()).isEqualTo(before.summary());
  }

  @Test
  void readersSeeOldGenerationDuringRebuildAndNewCommittedEventsCatchUpAfterward()
      throws Exception {
    final UUID orderId = seedOrder();
    long version = queries.projection(admin).version();
    var snapshotted = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var first = new AtomicBoolean(true);
    doAnswer(
            invocation -> {
              Object result = invocation.callRealMethod();
              if (first.getAndSet(false)) {
                snapshotted.countDown();
                assertThat(release.await(8, TimeUnit.SECONDS)).isTrue();
              }
              return result;
            })
        .when(AopTestUtils.<OrderFacts>getUltimateTargetObject(sourceOrders))
        .after(isNull(), eq(200));
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var rebuilding = executor.submit(() -> maintenance.rebuild(admin, version));
      try {
        assertThat(snapshotted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(reports.operations(admin, FROM, FROM).summary().submittedOrders()).isZero();
        lifecycle.cancel(customer, orderId, 0);
      } finally {
        release.countDown();
      }
      assertThat(rebuilding.get(10, TimeUnit.SECONDS).generation()).isEqualTo(2);
    }
    TestDatabase.awaitPublications(jdbc);
    assertThat(reports.operations(admin, FROM, FROM).summary().submittedOrders()).isEqualTo(1);
    assertThat(reports.operations(admin, FROM, FROM).summary().cancelledCohort()).isEqualTo(1);
  }

  @Test
  void uninitializedProjectionIsUnavailableAndMaintenanceRequiresCurrentAdminVersion()
      throws Exception {
    jdbc.sql("UPDATE reporting_projection SET initialized = false WHERE id = 1").update();
    mvc.perform(
            get("/api/v1/reports/operations")
                .param("from", FROM.toString())
                .param("to", TO.toString())
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("REPORTING_NOT_READY"));
    mvc.perform(
            post("/api/v1/reports/projection/rebuild")
                .header("Authorization", "Bearer " + staffToken)
                .contentType("application/json")
                .content("{\"version\":1}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/reports/projection/rebuild")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content("{\"version\":" + queries.projection(admin).version() + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.initialized").value(true));
  }

  @Test
  void reportQueryCountDoesNotGrowWithDaysOrMaterializeOrderEntities() {
    metrics();
    var statistics =
        entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
    boolean enabled = statistics.isStatisticsEnabled();
    try {
      statistics.setStatisticsEnabled(true);
      statistics.clear();
      reports.operations(admin, FROM, FROM);
      long shortCount = statistics.getPrepareStatementCount();
      statistics.clear();
      reports.operations(admin, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"));
      assertThat(statistics.getPrepareStatementCount())
          .isEqualTo(shortCount)
          .isLessThanOrEqualTo(18);
      assertThat(statistics.getEntityLoadCount()).isEqualTo(2);
      assertThat(statistics.getCollectionLoadCount()).isZero();
    } finally {
      statistics.setStatisticsEnabled(enabled);
    }
  }

  private void metrics() {
    UUID product = UUID.randomUUID();
    UUID paymentA = UUID.randomUUID();
    UUID paymentB = UUID.randomUUID();
    UUID paymentC = UUID.randomUUID();
    UUID refund = UUID.randomUUID();
    var a =
        fact(
            UUID.randomUUID(),
            product,
            "原名称",
            "10.05",
            2,
            "COMPLETED",
            4,
            Instant.parse("2026-09-18T15:59:30Z"),
            Instant.parse("2026-09-18T15:59:35Z"),
            Instant.parse("2026-09-18T16:00:00Z"),
            paymentA,
            "NONE",
            null);
    var b =
        fact(
            UUID.randomUUID(),
            product,
            "=HYPERLINK(\"https://invalid.example\")",
            "12.00",
            1,
            "COMPLETED",
            4,
            Instant.parse("2026-09-19T01:00:00Z"),
            Instant.parse("2026-09-19T01:01:00Z"),
            Instant.parse("2026-09-19T01:20:00Z"),
            paymentB,
            "NONE",
            null);
    var c =
        fact(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "已取消商品",
            "7.50",
            1,
            "CANCELLED",
            4,
            Instant.parse("2026-09-19T02:00:00Z"),
            Instant.parse("2026-09-19T02:01:00Z"),
            null,
            paymentC,
            "SUCCEEDED",
            refund);
    projector.order(a);
    projector.order(b);
    projector.order(c);
    projector.receipt(paymentA, a.id(), a.total(), a.paidAt());
    projector.receipt(paymentB, b.id(), b.total(), b.paidAt());
    projector.receipt(paymentC, c.id(), c.total(), c.paidAt());
    projector.refund(refund, paymentC, c.id(), c.total(), Instant.parse("2026-09-19T16:00:00Z"));
    projector.customer(UUID.randomUUID(), Instant.parse("2026-09-19T00:00:00.123456789Z"));
  }

  private OrderFacts.Snapshot fact(
      UUID id,
      UUID product,
      String name,
      String price,
      int quantity,
      String status,
      long version,
      Instant created,
      Instant paid,
      Instant completed,
      UUID payment,
      String refundStatus,
      UUID refund) {
    var unit = new BigDecimal(price);
    return new OrderFacts.Snapshot(
        id,
        customer.customerId(),
        version,
        status,
        unit.multiply(BigDecimal.valueOf(quantity)),
        created,
        paid,
        completed,
        status.equals("CANCELLED") ? Instant.parse("2026-09-19T16:00:00Z") : null,
        payment,
        refundStatus,
        refund,
        List.of(new OrderFacts.Line(UUID.randomUUID(), product, name, "DISH", quantity, unit)));
  }

  private UUID seedOrder() {
    var order =
        Order.submit(
            customer.customerId(),
            UUID.randomUUID().toString(),
            "fingerprint",
            new AddressSnapshot(
                UUID.randomUUID(), 0, "顾客", customer.phone(), "浙江省", "杭州市", "西湖区", "测试收货地址"),
            List.of(
                new OrderLine(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "DISH",
                    "面条",
                    new BigDecimal("18.50"),
                    1,
                    Map.of(),
                    List.of())),
            time.get());
    transactions.executeWithoutResult(status -> orderRepository.add(order));
    return order.id();
  }

  private UUID append() {
    UUID id = UUID.randomUUID();
    notifications.append(id, UUID.randomUUID(), Notice.Kind.NEW_ORDER, time.get());
    return id;
  }

  private PaymentGateway.TradeResult success(BigDecimal amount, String trade) {
    return new PaymentGateway.TradeResult(
        PaymentGateway.State.SUCCEEDED, trade, amount, time.get());
  }

  private StaffIdentity employee(String name, EmployeeAccount.Role role) {
    var employee =
        EmployeeAccount.create(
            UUID.randomUUID(),
            new EmployeeProfile(name, "员工", ""),
            "unused-fixture-hash",
            role,
            NOW);
    employees.add(employee);
    return new StaffIdentity(employee.id(), name, "员工", role.name(), 0);
  }

  private CustomerIdentity customer(String phone) {
    var account =
        CustomerAccount.create(UUID.randomUUID(), phone, "顾客", "unused-fixture-hash", NOW);
    customers.add(account);
    return new CustomerIdentity(account.id(), account.phone(), account.displayName(), 0);
  }

  private String staffToken(StaffIdentity actor) {
    String token = employeeTokens.newToken();
    employeeSessions.add(
        employeeTokens.digest(token), actor.employeeId(), 0, NOW.plusSeconds(86400));
    return token;
  }

  private String customerToken(CustomerIdentity actor) {
    String token = customerTokens.newToken();
    customerSessions.add(
        customerTokens.digest(token), actor.customerId(), 0, NOW.plusSeconds(86400));
    return token;
  }

  private WebSocket connect(String ticket, Messages listener, String origin) throws Exception {
    var builder =
        client.newWebSocketBuilder().subprotocols("han-menu.notifications.v1", "ticket." + ticket);
    if (origin != null) {
      builder.header("Origin", origin);
    }
    var result =
        builder
            .buildAsync(
                URI.create("ws://127.0.0.1:" + port + "/api/v1/notifications/stream"), listener)
            .get(5, TimeUnit.SECONDS);
    sockets.add(result);
    return result;
  }

  private void assertHandshakeDenied(String ticket, String origin, int expected) {
    assertThatThrownBy(() -> connect(ticket, new Messages(), origin))
        .hasCauseInstanceOf(WebSocketHandshakeException.class)
        .satisfies(
            exception ->
                assertThat(
                        ((WebSocketHandshakeException) exception.getCause())
                            .getResponse()
                            .statusCode())
                    .isEqualTo(expected));
  }

  /** 真实 JDK WebSocket 客户端按分片合并消息并记录关闭状态. */
  private static final class Messages implements WebSocket.Listener {
    private final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
    private final StringBuilder assembling = new StringBuilder();
    final CompletableFuture<Integer> closed = new CompletableFuture<>();

    @Override
    public void onOpen(WebSocket webSocket) {
      webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
      assembling.append(data);
      if (last) {
        frames.add(assembling.toString());
        assembling.setLength(0);
      }
      webSocket.request(1);
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int status, String reason) {
      closed.complete(status);
      return CompletableFuture.completedFuture(null);
    }

    String next() throws InterruptedException {
      String value = frames.poll(5, TimeUnit.SECONDS);
      assertThat(value).isNotNull();
      return value;
    }
  }
}
