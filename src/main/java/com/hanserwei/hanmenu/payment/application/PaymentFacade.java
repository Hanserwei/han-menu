package com.hanserwei.hanmenu.payment.application;

import com.hanserwei.hanmenu.payment.api.PaymentOperations;
import com.hanserwei.hanmenu.payment.domain.Payment;
import com.hanserwei.hanmenu.payment.domain.PaymentGateway;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 支付公开能力的无事务外观，将渠道参数生成和短事务操作明确分开. */
@Service
class PaymentFacade implements PaymentOperations {
  private final PaymentTransactions transactions;
  private final PaymentGateway gateway;
  private final Clock clock;

  PaymentFacade(PaymentTransactions transactions, PaymentGateway gateway, Clock clock) {
    this.transactions = transactions;
    this.gateway = gateway;
    this.clock = clock;
  }

  @Override
  public void requireConfigured() {
    gateway.requireConfigured();
  }

  @Override
  public Intent reserve(
      UUID businessRef,
      UUID customerId,
      BigDecimal amount,
      Instant expiresAt,
      String key,
      long orderVersion) {
    return transactions.reserve(businessRef, customerId, amount, expiresAt, key, orderVersion);
  }

  @Override
  public boolean submitted(UUID customerId, String key) {
    return transactions.submitted(customerId, key);
  }

  @Override
  public void stop(UUID id) {
    transactions.stop(id);
  }

  @Override
  public void refund(UUID id) {
    transactions.refund(id);
  }

  @Override
  public AppPayment parameters(UUID id, UUID customerId) {
    var payment = transactions.own(id, customerId);
    String orderString = null;
    if (payment.status() == Payment.Status.PENDING
        && !payment.closeRequested()
        && clock.instant().isBefore(payment.expiresAt())) {
      payment.requirePayable(clock.instant());
      orderString = gateway.appParameters(payment.request());
    }
    return new AppPayment(
        payment.id(),
        payment.status().name(),
        payment.amount(),
        "CNY",
        payment.expiresAt(),
        payment.version(),
        "ALIPAY_SANDBOX",
        "APP",
        orderString);
  }
}
