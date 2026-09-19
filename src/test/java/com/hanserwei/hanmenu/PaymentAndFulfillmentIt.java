package com.hanserwei.hanmenu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.hanserwei.hanmenu.ordering.application.OrderLifecycleService;
import com.hanserwei.hanmenu.ordering.application.OrderPaymentService;
import com.hanserwei.hanmenu.ordering.application.OrderService;
import com.hanserwei.hanmenu.ordering.domain.AddressSnapshot;
import com.hanserwei.hanmenu.ordering.domain.Order;
import com.hanserwei.hanmenu.ordering.domain.OrderLine;
import com.hanserwei.hanmenu.ordering.domain.OrderRepository;
import com.hanserwei.hanmenu.payment.application.PaymentReconciliation;
import com.hanserwei.hanmenu.payment.application.PaymentTransactions;
import com.hanserwei.hanmenu.payment.domain.PaymentException;
import com.hanserwei.hanmenu.payment.domain.PaymentGateway;
import com.hanserwei.hanmenu.payment.events.PaymentResult;
import com.hanserwei.hanmenu.support.TestDatabase;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.IncompleteEventPublications;
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

/** P5 真实数据库验证，渠道替身只提供测试事实，RSA2 与真实沙箱另行验收. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
@TestExecutionListeners(
    listeners = {TestDatabase.Cleanup.class, TestDatabase.RedisCleanup.class},
    mergeMode = MergeMode.MERGE_WITH_DEFAULTS)
class PaymentAndFulfillmentIt {
  private static final TestDatabase DATABASE = new TestDatabase(PaymentAndFulfillmentIt.class);
  private static final Instant NOW = Instant.parse("2026-09-19T00:00:00Z");
  private static final BigDecimal AMOUNT = new BigDecimal("18.50");
  @Autowired private MockMvc mvc;
  @Autowired private JdbcClient jdbc;
  @Autowired private CustomerRepository customers;
  @Autowired private CustomerSessionRepository customerSessions;
  @Autowired private CustomerTokenFactory customerTokens;
  @Autowired private EmployeeRepository employees;
  @Autowired private SessionRepository staffSessions;
  @Autowired private TokenFactory staffTokens;
  @Autowired private OrderRepository orderRepository;
  @Autowired private OrderService orders;
  @Autowired private OrderPaymentService orderPayments;
  @Autowired private PaymentTransactions payments;
  @Autowired private PaymentReconciliation reconciliation;
  @Autowired private TransactionTemplate transactions;
  @Autowired private jakarta.persistence.EntityManagerFactory entityManagerFactory;
  @Autowired private IncompleteEventPublications publications;
  @MockitoSpyBean private OrderLifecycleService lifecycle;
  @MockitoBean private PaymentGateway gateway;
  @MockitoBean private Clock clock;
  private final AtomicReference<Instant> time = new AtomicReference<>(NOW);
  private CustomerIdentity customer;
  private CustomerIdentity other;
  private StaffIdentity staff;
  private String token;
  private String otherToken;
  private String staffToken;
  private UUID orderId;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    DATABASE.configure(registry);
  }

  /** 会话直接写入测试专用仓储，HTTP 请求仍经过真实数据库认证和安全链. */
  @BeforeEach
  void fixtures() {
    time.set(NOW);
    when(clock.instant()).thenAnswer(ignored -> time.get());
    jdbc.sql(
            "TRUNCATE payment_refund, payment_intent, ordering_line, ordering_order,"
                + " event_publication,"
                + " customer_session, customer_address, customer_account,"
                + " identity_session, identity_employee, identity_audit CASCADE")
        .update();
    var first =
        CustomerAccount.create(
            UUID.randomUUID(), "+8613800138001", "支付测试顾客", "fixture-unused-hash", NOW);
    var second =
        CustomerAccount.create(
            UUID.randomUUID(), "+8613800138002", "其他顾客", "fixture-unused-hash", NOW);
    customers.add(first);
    customers.add(second);
    customer = new CustomerIdentity(first.id(), first.phone(), first.displayName(), 0);
    other = new CustomerIdentity(second.id(), second.phone(), second.displayName(), 0);
    token = customerTokens.newToken();
    otherToken = customerTokens.newToken();
    customerSessions.add(
        customerTokens.digest(token), customer.customerId(), 0, NOW.plusSeconds(86400));
    customerSessions.add(
        customerTokens.digest(otherToken), other.customerId(), 0, NOW.plusSeconds(86400));
    var employee =
        EmployeeAccount.create(
            UUID.randomUUID(),
            new EmployeeProfile("staff", "接单员工", ""),
            "fixture-unused-hash",
            EmployeeAccount.Role.STAFF,
            NOW);
    employees.add(employee);
    staff = new StaffIdentity(employee.id(), "staff", "接单员工", "STAFF", 0);
    staffToken = staffTokens.newToken();
    staffSessions.add(
        staffTokens.digest(staffToken), staff.employeeId(), 0, NOW.plusSeconds(86400));
    var order =
        Order.submit(
            customer.customerId(),
            "order-key",
            "fingerprint",
            new AddressSnapshot(
                UUID.randomUUID(), 0, "顾客", "+8613800138001", "浙江省", "杭州市", "西湖区", "测试地址"),
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
            NOW);
    transactions.executeWithoutResult(status -> orderRepository.add(order));
    orderId = order.id();
    when(gateway.appParameters(any()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return "test-signed-parameters";
            });
    when(gateway.query(any()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return new PaymentGateway.TradeResult(
                  PaymentGateway.State.NOT_FOUND, null, null, null);
            });
  }

  @Test
  void paymentCreationIsIdempotentOwnedAndUsesServerAmount() throws Exception {
    mvc.perform(
            post("/api/v1/orders/{id}/payments", orderId)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "payment-key")
                .contentType("application/json")
                .content("{\"version\":0}"))
        .andExpect(status().isCreated())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string("Idempotency-Replayed", "false"));
    mvc.perform(
            post("/api/v1/orders/{id}/payments", orderId)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "payment-key")
                .contentType("application/json")
                .content("{\"version\":0}"))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string("Idempotency-Replayed", "true"));
    var first = orderPayments.create(customer, orderId, "payment-key", 0);
    var replay = orderPayments.create(customer, orderId, "payment-key", 0);
    assertThat(first.payment().id()).isEqualTo(replay.payment().id());
    assertThat(first.payment().amount()).isEqualByComparingTo(AMOUNT);
    assertThat(orders.detail(customer, orderId).version()).isEqualTo(1);
    assertThat(count("payment_intent")).isEqualTo(1);
    mvc.perform(
            post("/api/v1/orders/{id}/payments", orderId)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "injected")
                .contentType("application/json")
                .content("{\"version\":1,\"amount\":0.01}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/v1/payments/{id}", first.payment().id())
                .header("Authorization", "Bearer " + otherToken))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/v1/payments/{id}", first.payment().id())
                .header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isUnauthorized());
    assertThatThrownBy(() -> orderPayments.create(other, orderId, "foreign", 0))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> orderPayments.create(customer, orderId, "payment-key", 1))
        .isInstanceOf(PaymentException.class);
    assertThatThrownBy(() -> orderPayments.create(customer, orderId, "other-key", 1))
        .isInstanceOf(PaymentException.class);
  }

  @Test
  void signedPaymentResultUnlocksStaffFulfillmentButCannotSkipSteps() throws Exception {
    final UUID payment = createPayment();
    assertThatThrownBy(() -> lifecycle.act(staff, orderId, 1, OrderLifecycleService.Action.ACCEPT))
        .isInstanceOf(RuntimeException.class);
    payments.observe(payment, result(PaymentGateway.State.SUCCEEDED));
    awaitStatus("PAID");
    mvc.perform(
            get("/api/v1/management/orders")
                .header("Authorization", "Bearer " + staffToken)
                .param("status", "PAID"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(orderId.toString()));
    mvc.perform(get("/api/v1/management/orders").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
    act("acceptance");
    assertThatThrownBy(() -> lifecycle.cancel(customer, orderId, version()))
        .isInstanceOf(RuntimeException.class);
    act("delivery");
    act("completion");
    payments.observe(payment, result(PaymentGateway.State.CLOSED));
    payments.observe(payment, result(PaymentGateway.State.SUCCEEDED));
    assertThat(orders.detail(customer, orderId).status()).isEqualTo("COMPLETED");
    assertThat(count("payment_refund")).isZero();
    assertThatThrownBy(
            () -> lifecycle.act(staff, orderId, version(), OrderLifecycleService.Action.COMPLETE))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void pendingCancellationRequiresChannelClosureAndNetworkCallsHaveNoTransaction()
      throws Exception {
    final UUID payment = createPayment();
    lifecycle.cancel(customer, orderId, version());
    assertThat(orders.detail(customer, orderId).status()).isEqualTo("CANCELLING");
    when(gateway.query(any())).thenReturn(result(PaymentGateway.State.PENDING));
    when(gateway.close(any()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return result(PaymentGateway.State.CLOSED);
            });
    reconciliation.payment(payment);
    awaitStatus("CANCELLED");
    assertThat(payments.view(customer, payment).status()).isEqualTo("CLOSED");
    assertThat(count("payment_refund")).isZero();
  }

  @Test
  void lostChannelResponseKeepsIntentForRetryWithoutFakingSuccess() throws Exception {
    final UUID payment = createPayment();
    lifecycle.cancel(customer, orderId, version());
    when(gateway.query(any()))
        .thenThrow(new PaymentException(PaymentException.Reason.UNAVAILABLE, "模拟渠道超时"));
    reconciliation.payment(payment);
    assertThat(payments.view(customer, payment).status()).isEqualTo("PENDING");
    assertThat(payments.view(customer, payment).lastFailure()).isEqualTo("CHANNEL_UNAVAILABLE");
    assertThat(orders.detail(customer, orderId).status()).isEqualTo("CANCELLING");
    time.set(NOW.plusSeconds(31));
    doReturn(result(PaymentGateway.State.CLOSED)).when(gateway).query(any());
    reconciliation.payment(payment);
    awaitStatus("CANCELLED");
  }

  @Test
  void lateSuccessfulPaymentAfterCancellationRefundsWithoutReopeningOrder() throws Exception {
    final UUID payment = createPayment();
    lifecycle.cancel(customer, orderId, version());
    payments.observe(payment, result(PaymentGateway.State.CLOSED));
    awaitStatus("CANCELLED");
    payments.observe(payment, result(PaymentGateway.State.SUCCEEDED));
    await(() -> orders.detail(customer, orderId).lifecycle().refundStatus().equals("PENDING"));
    assertThat(orders.detail(customer, orderId).status()).isEqualTo("CANCELLED");
    UUID refund = payments.view(customer, payment).refundId();
    assertThat(refund).isNotNull();
    when(gateway.refundSucceeded(any())).thenReturn(false, true);
    doAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return null;
            })
        .when(gateway)
        .refund(any());
    reconciliation.refund(refund);
    await(() -> orders.detail(customer, orderId).lifecycle().refundStatus().equals("SUCCEEDED"));
    assertThat(orders.detail(customer, orderId).status()).isEqualTo("CANCELLED");
    payments.observe(payment, result(PaymentGateway.State.SUCCEEDED));
    assertThat(count("payment_refund")).isEqualTo(1);
    mvc.perform(get("/api/v1/refunds/{id}", refund).header("Authorization", "Bearer " + otherToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void merchantRejectionUsesFullAmountAndRefundResponseLossRecoversByQuery() throws Exception {
    final UUID payment = createPayment();
    payments.observe(payment, result(PaymentGateway.State.SUCCEEDED));
    awaitStatus("PAID");
    lifecycle.act(staff, orderId, version(), OrderLifecycleService.Action.REJECT);
    assertThat(orders.detail(customer, orderId).status()).isEqualTo("REFUNDING");
    UUID refund = payments.view(customer, payment).refundId();
    var channelRefunded = new AtomicBoolean();
    when(gateway.refundSucceeded(any())).thenAnswer(invocation -> channelRefunded.get());
    doAnswer(
            invocation -> {
              PaymentGateway.RefundRequest request = invocation.getArgument(0);
              assertThat(request.amount()).isEqualByComparingTo(AMOUNT);
              assertThat(request.refundId()).isEqualTo(refund);
              channelRefunded.set(true);
              throw new PaymentException(PaymentException.Reason.UNAVAILABLE, "退款响应丢失");
            })
        .when(gateway)
        .refund(any());
    reconciliation.refund(refund);
    assertThat(payments.refundView(customer, refund).status()).isEqualTo("PENDING");
    time.set(NOW.plusSeconds(31));
    reconciliation.refund(refund);
    awaitStatus("CANCELLED");
    assertThat(payments.refundView(customer, refund).status()).isEqualTo("SUCCEEDED");
    verify(gateway, times(1)).refund(any());
  }

  @Test
  void merelyAcceptedRefundRemainsPendingUntilQueryConfirms() throws Exception {
    final UUID payment = createPayment();
    payments.observe(payment, result(PaymentGateway.State.SUCCEEDED));
    awaitStatus("PAID");
    lifecycle.cancel(customer, orderId, version());
    UUID refund = payments.view(customer, payment).refundId();
    when(gateway.refundSucceeded(any())).thenReturn(false);
    reconciliation.refund(refund);
    assertThat(orders.detail(customer, orderId).status()).isEqualTo("REFUNDING");
    assertThat(payments.refundView(customer, refund).confirmedAt()).isNull();
    time.set(NOW.plusSeconds(31));
    when(gateway.refundSucceeded(any())).thenReturn(true);
    reconciliation.refund(refund);
    awaitStatus("CANCELLED");
  }

  @Test
  void expiryWithoutPaymentCancelsAndWithIssuedParametersWaitsForSafeAbsenceWindow()
      throws Exception {
    time.set(NOW.plusSeconds(900));
    lifecycle.expire(orderId);
    assertThat(orders.detail(customer, orderId).status()).isEqualTo("CANCELLED");
    assertThatThrownBy(() -> orderPayments.create(customer, orderId, "too-late", version()))
        .isInstanceOf(RuntimeException.class);
    assertThat(count("payment_intent")).isZero();
    time.set(NOW);
    var fresh =
        Order.submit(customer.customerId(), "other", "digest", snapshot(), List.of(line()), NOW);
    transactions.executeWithoutResult(status -> orderRepository.add(fresh));
    orderId = fresh.id();
    final UUID payment = createPayment();
    time.set(NOW.plusSeconds(901));
    lifecycle.expire(orderId);
    reconciliation.payment(payment);
    assertThat(orders.detail(customer, orderId).status()).isEqualTo("CANCELLING");
    assertThat(payments.view(customer, payment).status()).isEqualTo("PENDING");
    time.set(NOW.plusSeconds(1021));
    reconciliation.payment(payment);
    awaitStatus("CANCELLED");
  }

  @Test
  void invalidNotificationIsRejectedAndValidDuplicateDeliveryIsIdempotent() throws Exception {
    final UUID payment = createPayment();
    when(gateway.verify(any()))
        .thenThrow(new PaymentException(PaymentException.Reason.INVALID_NOTIFICATION, "验签失败"));
    mvc.perform(
            post("/api/v1/payment-notifications/alipay")
                .contentType("application/x-www-form-urlencoded")
                .param("sign", "forged"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string("failure"));
    mvc.perform(
            post("/api/v1/payment-notifications/alipay")
                .contentType("application/x-www-form-urlencoded")
                .param("sign", "one", "two"))
        .andExpect(status().isBadRequest());
    doReturn(new PaymentGateway.Notice(payment, result(PaymentGateway.State.SUCCEEDED)))
        .when(gateway)
        .verify(any());
    for (int i = 0; i < 2; i++) {
      mvc.perform(
              post("/api/v1/payment-notifications/alipay")
                  .contentType("application/x-www-form-urlencoded")
                  .param("sign", "test-verified-by-gateway"))
          .andExpect(status().isOk())
          .andExpect(content().string("success"));
    }
    awaitStatus("PAID");
    assertThat(count("payment_intent")).isEqualTo(1);
  }

  @Test
  void paymentStateAndReliableEventRegistrationRollbackTogether() {
    final UUID payment = createPayment();
    transactions.executeWithoutResult(
        status -> {
          payments.observe(payment, result(PaymentGateway.State.SUCCEEDED));
          org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(
                  entityManagerFactory)
              .flush();
          assertThat(count("event_publication")).isEqualTo(1);
          status.setRollbackOnly();
        });
    assertThat(count("event_publication")).isZero();
    assertThat(payments.view(customer, payment).status()).isEqualTo("PENDING");
    assertThat(orders.detail(customer, orderId).status()).isEqualTo("UNPAID");
  }

  @Test
  void failedConsumerRemainsDurableAndCanBeReplayed() throws Exception {
    final UUID payment = createPayment();
    var failOnce = new AtomicBoolean(true);
    doAnswer(
            invocation -> {
              if (failOnce.getAndSet(false)) {
                throw new IllegalStateException("模拟消费者事务失败");
              }
              return invocation.callRealMethod();
            })
        .when(AopTestUtils.<OrderLifecycleService>getUltimateTargetObject(lifecycle))
        .paymentResult(any(PaymentResult.class));
    payments.observe(payment, result(PaymentGateway.State.SUCCEEDED));
    await(
        () ->
            !failOnce.get()
                && jdbc.sql("SELECT count(*) FROM event_publication WHERE status = 'FAILED'")
                        .query(Long.class)
                        .single()
                    == 1);
    assertThat(orders.detail(customer, orderId).status()).isEqualTo("UNPAID");
    time.set(NOW.plusSeconds(120));
    publications.resubmitIncompletePublications(
        org.springframework.modulith.events.ResubmissionOptions.defaults()
            .withMinAge(Duration.ofSeconds(1))
            .withBatchSize(10)
            .withMaxInFlight(1));
    awaitStatus("PAID");
    await(() -> count("event_publication") == 0);
  }

  @Test
  void concurrentAcceptAndCancellationCannotBothSucceed() throws Exception {
    final UUID payment = createPayment();
    payments.observe(payment, result(PaymentGateway.State.SUCCEEDED));
    awaitStatus("PAID");
    long expected = version();
    var gate = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var accept =
          executor.submit(
              () -> {
                gate.await();
                return attempt(
                    () ->
                        lifecycle.act(
                            staff, orderId, expected, OrderLifecycleService.Action.ACCEPT));
              });
      var cancel =
          executor.submit(
              () -> {
                gate.await();
                return attempt(() -> lifecycle.cancel(customer, orderId, expected));
              });
      gate.countDown();
      assertThat(List.of(accept.get(10, TimeUnit.SECONDS), cancel.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(orders.detail(customer, orderId).status()).isIn("ACCEPTED", "REFUNDING");
    assertThat(count("payment_refund")).isLessThanOrEqualTo(1);
  }

  @Test
  void configurationFailureAndVersionConflictsCannotCreateExtraIntents() throws Exception {
    doThrow(new PaymentException(PaymentException.Reason.UNAVAILABLE, "沙箱未配置"))
        .when(gateway)
        .requireConfigured();
    mvc.perform(
            post("/api/v1/orders/{id}/payments", orderId)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "config")
                .contentType("application/json")
                .content("{\"version\":0}"))
        .andExpect(status().isServiceUnavailable());
    assertThat(count("payment_intent")).isZero();
    verify(gateway, never()).appParameters(any());
  }

  @Test
  void expiryLosesToConfirmedPaymentAndDisabledStaffCannotProcessOrders() throws Exception {
    final UUID payment = createPayment();
    payments.observe(payment, result(PaymentGateway.State.SUCCEEDED));
    awaitStatus("PAID");
    time.set(NOW.plusSeconds(901));
    lifecycle.expire(orderId);
    assertThat(orders.detail(customer, orderId).status()).isEqualTo("PAID");
    var employee = employees.findById(staff.employeeId()).orElseThrow();
    employee.changeEnabled(false, time.get());
    employees.update(employee);
    assertThatThrownBy(
            () -> lifecycle.act(staff, orderId, version(), OrderLifecycleService.Action.ACCEPT))
        .isInstanceOf(RuntimeException.class);
    mvc.perform(get("/api/v1/management/orders").header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isUnauthorized());
  }

  private UUID createPayment() {
    return orderPayments.create(customer, orderId, "payment-key", 0).payment().id();
  }

  private long version() {
    return orders.detail(customer, orderId).version();
  }

  private long count(String table) {
    return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
  }

  private PaymentGateway.TradeResult result(PaymentGateway.State state) {
    return new PaymentGateway.TradeResult(
        state,
        "2026091922001400000000000001",
        AMOUNT,
        state == PaymentGateway.State.SUCCEEDED ? NOW : null);
  }

  private void act(String action) throws Exception {
    mvc.perform(
            post("/api/v1/management/orders/{id}/" + action, orderId)
                .header("Authorization", "Bearer " + staffToken)
                .contentType("application/json")
                .content("{\"version\":" + version() + "}"))
        .andExpect(status().isOk());
  }

  private void awaitStatus(String status) throws Exception {
    await(() -> orders.detail(customer, orderId).status().equals(status));
  }

  private void await(BooleanSupplier condition) throws Exception {
    long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
    while (!condition.getAsBoolean() && System.nanoTime() < end) {
      Thread.sleep(20);
    }
    assertThat(condition.getAsBoolean()).isTrue();
  }

  private boolean attempt(Runnable action) {
    try {
      action.run();
      return true;
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private AddressSnapshot snapshot() {
    return new AddressSnapshot(
        UUID.randomUUID(), 0, "顾客", "+8613800138001", "浙江省", "杭州市", "西湖区", "测试地址");
  }

  private OrderLine line() {
    return new OrderLine(
        UUID.randomUUID(), UUID.randomUUID(), "DISH", "面条", AMOUNT, 1, Map.of(), List.of());
  }
}
