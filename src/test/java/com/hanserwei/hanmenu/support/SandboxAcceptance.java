package com.hanserwei.hanmenu.support;

import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradeCreateModel;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.request.AlipayTradeCreateRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.hanserwei.hanmenu.HanMenuApplication;
import com.hanserwei.hanmenu.customer.api.CustomerIdentity;
import com.hanserwei.hanmenu.customer.domain.CustomerAccount;
import com.hanserwei.hanmenu.customer.domain.CustomerRepository;
import com.hanserwei.hanmenu.ordering.application.OrderLifecycleService;
import com.hanserwei.hanmenu.ordering.application.OrderPaymentService;
import com.hanserwei.hanmenu.ordering.application.OrderService;
import com.hanserwei.hanmenu.ordering.domain.AddressSnapshot;
import com.hanserwei.hanmenu.ordering.domain.Order;
import com.hanserwei.hanmenu.ordering.domain.OrderLine;
import com.hanserwei.hanmenu.ordering.domain.OrderRepository;
import com.hanserwei.hanmenu.payment.application.PaymentReconciliation;
import com.hanserwei.hanmenu.payment.application.PaymentTransactions;
import com.hanserwei.hanmenu.payment.domain.PaymentGateway;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** 手动真实沙箱验收入口，只使用随机测试 schema、模拟买家与 0.01 元测试交易. */
public final class SandboxAcceptance {
  private final ConfigurableApplicationContext context;
  private final CustomerIdentity customer;
  private final DefaultAlipayClient client;
  private final CountDownLatch stop = new CountDownLatch(1);
  private UUID orderId;
  private UUID paymentId;

  private SandboxAcceptance(ConfigurableApplicationContext context) {
    this.context = context;
    var account =
        CustomerAccount.create(
            UUID.randomUUID(),
            "+8613800138099",
            "沙箱验收顾客",
            "manual-fixture-without-login",
            Instant.now());
    context.getBean(CustomerRepository.class).add(account);
    customer = new CustomerIdentity(account.id(), account.phone(), account.displayName(), 0);
    client =
        new DefaultAlipayClient(
            "https://openapi-sandbox.dl.alipaydev.com/gateway.do",
            System.getenv("ALIPAY_APP_ID"),
            System.getenv("ALIPAY_PRIVATE_KEY"),
            "json",
            "UTF-8",
            System.getenv("ALIPAY_PUBLIC_KEY"),
            "RSA2");
    client.setConnectTimeout(3000);
    client.setReadTimeout(8000);
  }

  /**
   * 启动隔离测试应用及仅本机可访问的验收控制页；结束后清理本次 schema.
   *
   * @param args 不接受业务参数，金额和环境固定为沙箱 0.01 元
   * @throws Exception 基础设施或沙箱校验不通过时终止验收
   */
  public static void main(String[] args) throws Exception {
    if (!"true".equals(System.getenv("ALIPAY_SANDBOX_ACCEPTANCE"))) {
      throw new IllegalArgumentException("手动验收必须显式设置 ALIPAY_SANDBOX_ACCEPTANCE=true");
    }
    try (var database = new TestDatabase(SandboxAcceptance.class)) {
      var arguments = new ArrayList<String>();
      database.configure((key, value) -> arguments.add("--" + key + "=" + value.get()));
      arguments.add("--server.port=8185");
      arguments.add("--server.address=127.0.0.1");
      arguments.add("--han-menu.payment.scheduling-enabled=true");
      var application = new SpringApplication(HanMenuApplication.class);
      application.setAdditionalProfiles("test");
      try (var context = application.run(arguments.toArray(String[]::new))) {
        context.getBean(PaymentGateway.class).requireConfigured();
        var acceptance = new SandboxAcceptance(context);
        acceptance.prepare();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8186), 0);
        server.createContext("/", acceptance::handle);
        server.start();
        System.out.println("SANDBOX_SMOKE_READY http://127.0.0.1:8186/checkout");
        try {
          acceptance.stop.await();
        } finally {
          server.stop(0);
        }
      }
    }
  }

  private void prepare() {
    var order =
        Order.submit(
            customer.customerId(),
            UUID.randomUUID().toString(),
            "sandbox-acceptance",
            new AddressSnapshot(
                UUID.randomUUID(), 0, "测试收货人", "+8613800138099", "浙江省", "杭州市", "西湖区", "沙箱测试地址"),
            List.of(
                new OrderLine(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "DISH",
                    "沙箱验收商品",
                    new BigDecimal("0.01"),
                    1,
                    Map.of(),
                    List.of())),
            Instant.now());
    context
        .getBean(TransactionTemplate.class)
        .executeWithoutResult(status -> context.getBean(OrderRepository.class).add(order));
    orderId = order.id();
    paymentId =
        context
            .getBean(OrderPaymentService.class)
            .create(customer, orderId, UUID.randomUUID().toString(), 0)
            .payment()
            .id();
  }

  private void handle(HttpExchange exchange) {
    try {
      String path = exchange.getRequestURI().getPath();
      if (exchange.getRequestMethod().equals("POST")
          && (!"local".equals(exchange.getRequestHeaders().getFirst("X-Han-Menu-Probe"))
              || exchange.getRequestHeaders().containsKey("Origin"))) {
        respond(exchange, 403, "text/plain", "denied");
        return;
      }
      if (exchange.getRequestMethod().equals("POST") && path.equals("/stop")) {
        respond(exchange, 200, "text/plain", "stopping");
        stop.countDown();
        return;
      }
      if (exchange.getRequestMethod().equals("POST") && path.equals("/new")) {
        prepare();
      } else if (exchange.getRequestMethod().equals("POST") && path.equals("/cancel")) {
        var detail = context.getBean(OrderService.class).detail(customer, orderId);
        context.getBean(OrderLifecycleService.class).cancel(customer, orderId, detail.version());
      } else if (exchange.getRequestMethod().equals("POST") && path.equals("/create-channel")) {
        var model = new AlipayTradeCreateModel();
        model.setOutTradeNo(paymentId.toString());
        model.setTotalAmount("0.01");
        model.setSubject("Han Menu 沙箱关单验收");
        model.setBuyerId(System.getenv("ALIPAY_SANDBOX_BUYER_ID"));
        var request = new AlipayTradeCreateRequest();
        request.setBizModel(model);
        var response = client.execute(request);
        if (!response.isSuccess()) {
          throw new IllegalStateException("沙箱创建交易失败：" + response.getSubCode());
        }
      } else if (exchange.getRequestMethod().equals("GET") && path.equals("/checkout")) {
        var model = new AlipayTradePagePayModel();
        model.setOutTradeNo(paymentId.toString());
        model.setTotalAmount("0.01");
        model.setSubject("Han Menu 沙箱验收");
        model.setProductCode("FAST_INSTANT_TRADE_PAY");
        model.setTimeExpire(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .format(
                    context
                        .getBean(OrderService.class)
                        .detail(customer, orderId)
                        .expiresAt()
                        .atZone(ZoneId.of("Asia/Shanghai"))));
        var request = new AlipayTradePagePayRequest();
        request.setBizModel(model);
        request.setNotifyUrl(System.getenv("ALIPAY_NOTIFY_URL"));
        request.setReturnUrl("http://127.0.0.1:8186/status");
        respond(exchange, 200, "text/html; charset=UTF-8", client.pageExecute(request).getBody());
        return;
      } else if (!path.equals("/status")) {
        respond(exchange, 404, "text/plain", "not found");
        return;
      }
      context.getBean(PaymentReconciliation.class).reconcile();
      var payment = context.getBean(PaymentTransactions.class).view(customer, paymentId);
      var order = context.getBean(OrderService.class).detail(customer, orderId);
      var result = new java.util.LinkedHashMap<String, Object>();
      result.put("orderId", orderId);
      result.put("paymentId", paymentId);
      result.put("paymentStatus", payment.status());
      result.put("orderStatus", order.status());
      result.put("refundId", payment.refundId());
      result.put("lastFailure", payment.lastFailure());
      result.put("refundStatus", order.lifecycle().refundStatus());
      respond(
          exchange,
          200,
          "application/json",
          context.getBean(JsonMapper.class).writeValueAsString(result));
    } catch (Exception exception) {
      try {
        respond(
            exchange,
            500,
            "text/plain",
            "sandbox_probe_failed:" + exception.getClass().getSimpleName());
      } catch (Exception ignored) {
        exchange.close();
      }
    }
  }

  private void respond(HttpExchange exchange, int status, String type, String body)
      throws java.io.IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", type);
    exchange.sendResponseHeaders(status, bytes.length);
    try (var output = exchange.getResponseBody()) {
      output.write(bytes);
    }
  }
}
