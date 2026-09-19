package com.hanserwei.hanmenu.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 全额退款聚合以固定退款号实现渠道幂等，只有查询确认后才完成. */
public final class Refund {
  private final UUID id;
  private final UUID paymentId;
  private final UUID businessRef;
  private final UUID customerId;
  private final String tradeNo;
  private final BigDecimal amount;
  private final Instant createdAt;
  private Status status;
  private Instant confirmedAt;
  private Instant nextAttemptAt;
  private String lastFailure;
  private final long version;

  /** 从持久化状态重建聚合，不执行渠道请求. */
  public Refund(
      UUID id,
      UUID paymentId,
      UUID businessRef,
      UUID customerId,
      String tradeNo,
      BigDecimal amount,
      Instant createdAt,
      Status status,
      Instant confirmedAt,
      Instant nextAttemptAt,
      String lastFailure,
      long version) {
    this.id = id;
    this.paymentId = paymentId;
    this.businessRef = businessRef;
    this.customerId = customerId;
    this.tradeNo = tradeNo;
    this.amount = amount;
    this.createdAt = createdAt;
    this.status = status;
    this.confirmedAt = confirmedAt;
    this.nextAttemptAt = nextAttemptAt;
    this.lastFailure = lastFailure;
    this.version = version;
    Objects.requireNonNull(id);
    Objects.requireNonNull(customerId);
    Objects.requireNonNull(businessRef);
    if (amount == null || amount.signum() <= 0 || amount.scale() > 2 || version < 0) {
      throw new IllegalArgumentException("支付快照金额或版本不合法");
    }
  }

  /** 仅允许已确认付款的支付单生成一个全额退款意图. */
  public static Refund create(Payment payment, Instant now) {
    if (payment.status() != Payment.Status.SUCCEEDED) {
      throw new PaymentException(PaymentException.Reason.CONFLICT, "支付未成功，不能退款");
    }
    return new Refund(
        UUID.randomUUID(),
        payment.id(),
        payment.businessRef(),
        payment.customerId(),
        payment.tradeNo(),
        payment.amount(),
        now,
        Status.PENDING,
        null,
        now,
        null,
        0);
  }

  /** 短事务领取工作，外部调用失败或进程退出后可恢复. */
  public boolean claim(Instant now) {
    if (status != Status.PENDING || nextAttemptAt == null || nextAttemptAt.isAfter(now)) {
      return false;
    }
    nextAttemptAt = now.plusSeconds(60);
    return true;
  }

  /** 保存固定失败原因并持续查询或重试同一退款号. */
  public void retry(Instant now) {
    if (status == Status.PENDING) {
      lastFailure = "CHANNEL_UNAVAILABLE";
      nextAttemptAt = now.plusSeconds(30);
    }
  }

  /** 渠道查询确认成功后终结退款；重复回执不重复发布结果. */
  public boolean confirm(Instant now) {
    if (status == Status.SUCCEEDED) {
      return false;
    }
    status = Status.SUCCEEDED;
    confirmedAt = now;
    nextAttemptAt = null;
    lastFailure = null;
    return true;
  }

  /** 返回固定退款号与原交易的渠道请求. */
  public PaymentGateway.RefundRequest request() {
    return new PaymentGateway.RefundRequest(id, paymentId, tradeNo, amount);
  }

  /** 受理、异常和结果不明始终保留为处理中. */
  public enum Status {
    PENDING,
    SUCCEEDED
  }

  /** 返回退款单标识. */
  public UUID id() {
    return id;
  }

  /** 返回原支付单标识. */
  public UUID paymentId() {
    return paymentId;
  }

  /** 返回业务引用. */
  public UUID businessRef() {
    return businessRef;
  }

  /** 返回顾客标识. */
  public UUID customerId() {
    return customerId;
  }

  /** 返回原渠道交易号. */
  public String tradeNo() {
    return tradeNo;
  }

  /** 返回全额退款金额. */
  public BigDecimal amount() {
    return amount;
  }

  /** 返回退款申请时间. */
  public Instant createdAt() {
    return createdAt;
  }

  /** 返回退款确认状态. */
  public Status status() {
    return status;
  }

  /** 返回退款确认时间. */
  public Instant confirmedAt() {
    return confirmedAt;
  }

  /** 返回下次尝试时间. */
  public Instant nextAttemptAt() {
    return nextAttemptAt;
  }

  /** 返回固定失败分类. */
  public String lastFailure() {
    return lastFailure;
  }

  /** 返回持久化版本. */
  public long version() {
    return version;
  }
}
