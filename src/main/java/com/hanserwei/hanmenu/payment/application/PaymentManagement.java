package com.hanserwei.hanmenu.payment.application;

import com.hanserwei.hanmenu.identity.api.StaffAuthorization;
import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import com.hanserwei.hanmenu.payment.domain.Payment;
import com.hanserwei.hanmenu.payment.domain.PaymentException;
import com.hanserwei.hanmenu.payment.domain.PaymentRepository;
import com.hanserwei.hanmenu.payment.domain.Refund;
import com.hanserwei.hanmenu.payment.domain.TransactionSearch;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理员资金明细查询，不反向访问订单模块，也不触发渠道调用或改变交易状态. */
@Service
@Transactional(readOnly = true)
public class PaymentManagement {
  private final StaffAuthorization staff;
  private final PaymentRepository repository;

  /** 注入当前员工授权契约和本模块仓储. */
  public PaymentManagement(StaffAuthorization staff, PaymentRepository repository) {
    this.staff = staff;
    this.repository = repository;
  }

  /** 管理员按创建时刻和业务引用分页查询支付意图. */
  public TransactionPageView<ManagedPaymentView> payments(
      StaffIdentity actor, Payment.Status status, TransactionSearch search) {
    staff.requireAdministrator(actor);
    var result = repository.searchPayments(status, search);
    return new TransactionPageView<>(
        result.items().stream().map(ManagedPaymentView::from).toList(),
        search.page(),
        search.size(),
        result.totalElements(),
        Math.ceilDiv(result.totalElements(), search.size()));
  }

  /** 管理员读取支付事实和重试状态，响应不包含幂等键或签名串. */
  public ManagedPaymentView payment(StaffIdentity actor, UUID id) {
    staff.requireAdministrator(actor);
    return ManagedPaymentView.from(
        repository
            .payment(id)
            .orElseThrow(() -> new PaymentException(PaymentException.Reason.NOT_FOUND, "支付单不存在")));
  }

  /** 管理员分页查询退款，不把受理状态当作退款成功. */
  public TransactionPageView<ManagedRefundView> refunds(
      StaffIdentity actor, Refund.Status status, TransactionSearch search) {
    staff.requireAdministrator(actor);
    var result = repository.searchRefunds(status, search);
    return new TransactionPageView<>(
        result.items().stream().map(ManagedRefundView::from).toList(),
        search.page(),
        search.size(),
        result.totalElements(),
        Math.ceilDiv(result.totalElements(), search.size()));
  }

  /** 管理员读取服务端确认的退款事实. */
  public ManagedRefundView refund(StaffIdentity actor, UUID id) {
    staff.requireAdministrator(actor);
    return ManagedRefundView.from(
        repository
            .refund(id)
            .orElseThrow(() -> new PaymentException(PaymentException.Reason.NOT_FOUND, "退款单不存在")));
  }

  /** 支付管理视图仅提供固定业务字段，不暴露 SDK 请求参数. */
  public record ManagedPaymentView(
      UUID id,
      UUID orderId,
      UUID customerId,
      BigDecimal amount,
      String currency,
      Payment.Status status,
      String tradeNo,
      Instant createdAt,
      Instant expiresAt,
      Instant paidAt,
      boolean closeRequested,
      Instant nextAttemptAt,
      String lastFailure,
      long version) {
    static ManagedPaymentView from(Payment value) {
      return new ManagedPaymentView(
          value.id(),
          value.businessRef(),
          value.customerId(),
          value.amount(),
          "CNY",
          value.status(),
          value.tradeNo(),
          value.createdAt(),
          value.expiresAt(),
          value.paidAt(),
          value.closeRequested(),
          value.nextAttemptAt(),
          value.lastFailure(),
          value.version());
    }
  }

  /** 退款管理视图保留原支付和订单引用，便于页面之间追踪. */
  public record ManagedRefundView(
      UUID id,
      UUID paymentId,
      UUID orderId,
      UUID customerId,
      BigDecimal amount,
      String currency,
      Refund.Status status,
      String tradeNo,
      Instant createdAt,
      Instant confirmedAt,
      Instant nextAttemptAt,
      String lastFailure,
      long version) {
    static ManagedRefundView from(Refund value) {
      return new ManagedRefundView(
          value.id(),
          value.paymentId(),
          value.businessRef(),
          value.customerId(),
          value.amount(),
          "CNY",
          value.status(),
          value.tradeNo(),
          value.createdAt(),
          value.confirmedAt(),
          value.nextAttemptAt(),
          value.lastFailure(),
          value.version());
    }
  }

  /** 支付或退款的零基分页 DTO. */
  public record TransactionPageView<T>(
      List<T> items, int page, int size, long totalElements, long totalPages) {
    /** 固定响应集合. */
    public TransactionPageView {
      items = List.copyOf(items);
    }
  }
}
