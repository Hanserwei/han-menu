package com.hanserwei.hanmenu.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 支付聚合维护单调渠道事实；关闭后收到真实成功仍记账并交给订单补偿. */
public final class Payment {
  private final UUID id;
  private final UUID businessRef;
  private final UUID customerId;
  private final BigDecimal amount;
  private final String idempotencyKey;
  private final String fingerprint;
  private final Instant createdAt;
  private final Instant expiresAt;
  private Status status;
  private String tradeNo;
  private Instant paidAt;
  private boolean closeRequested;
  private Instant nextAttemptAt;
  private String lastFailure;
  private final long version;

  /** 从持久化状态重建聚合，不执行渠道请求. */
  public Payment(
      UUID id,
      UUID businessRef,
      UUID customerId,
      BigDecimal amount,
      String idempotencyKey,
      String fingerprint,
      Instant createdAt,
      Instant expiresAt,
      Status status,
      String tradeNo,
      Instant paidAt,
      boolean closeRequested,
      Instant nextAttemptAt,
      String lastFailure,
      long version) {
    this.id = id;
    this.businessRef = businessRef;
    this.customerId = customerId;
    this.amount = amount;
    this.idempotencyKey = idempotencyKey;
    this.fingerprint = fingerprint;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
    this.status = status;
    this.tradeNo = tradeNo;
    this.paidAt = paidAt;
    this.closeRequested = closeRequested;
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

  /** 新建支付意图，不调用渠道且不能伪造付款成功. */
  public static Payment create(
      UUID businessRef,
      UUID customerId,
      BigDecimal amount,
      String key,
      String fingerprint,
      Instant now,
      Instant expiresAt) {
    if (!expiresAt.isAfter(now)) {
      throw new PaymentException(PaymentException.Reason.CONFLICT, "支付有效期已结束");
    }
    return new Payment(
        UUID.randomUUID(),
        businessRef,
        customerId,
        amount,
        key,
        fingerprint,
        now,
        expiresAt,
        Status.PENDING,
        null,
        null,
        false,
        now,
        null,
        0);
  }

  /** 同一幂等键必须对应同一订单版本与金额. */
  public void requireSameRequest(String expected) {
    if (!fingerprint.equals(expected)) {
      throw new PaymentException(PaymentException.Reason.CONFLICT, "支付幂等键已用于其他请求");
    }
  }

  /** 拒绝陈旧客户端手动刷新请求. */
  public void requireVersion(long expected) {
    if (version != expected) {
      throw new PaymentException(PaymentException.Reason.VERSION_CONFLICT, "支付单已更新");
    }
  }

  /** 只有未取消且未过期的待支付单可以签发 App 参数. */
  public void requirePayable(Instant now) {
    if (status != Status.PENDING || closeRequested || !now.isBefore(expiresAt)) {
      throw new PaymentException(PaymentException.Reason.CONFLICT, "支付单已关闭、已付款或已过期");
    }
  }

  /** 保存关闭意图，不能以请求已受理代替渠道确认. */
  public void requestClose(Instant now) {
    closeRequested = true;
    if (status == Status.PENDING) {
      nextAttemptAt = now;
    }
  }

  /** 在短事务中占用一次处理窗口，崩溃后窗口到期可重新领取. */
  public boolean claim(Instant now) {
    if (nextAttemptAt == null || nextAttemptAt.isAfter(now)) {
      return false;
    }
    nextAttemptAt = now.plusSeconds(60);
    return true;
  }

  /** 保存失败分类并安排重试，绝不因超时或异常改变支付事实. */
  public void retry(Instant now) {
    lastFailure = "CHANNEL_UNAVAILABLE";
    schedule(now);
  }

  /** 应用验签或主动查询得到的事实，返回是否需要发布新的支付状态事件. */
  public boolean observe(PaymentGateway.TradeResult result, Instant now) {
    if (result.state() != PaymentGateway.State.NOT_FOUND) {
      if (result.amount() == null
          || amount.compareTo(result.amount()) != 0
          || result.tradeNo() == null
          || result.tradeNo().isBlank()
          || (tradeNo != null && !tradeNo.equals(result.tradeNo()))) {
        throw new PaymentException(PaymentException.Reason.INVALID_NOTIFICATION, "渠道交易标识或金额不匹配");
      }
      tradeNo = result.tradeNo();
    }
    boolean changed = false;
    if (result.state() == PaymentGateway.State.SUCCEEDED && status != Status.SUCCEEDED) {
      if (result.paidAt() == null) {
        throw new PaymentException(PaymentException.Reason.INVALID_NOTIFICATION, "成功交易缺少付款时间");
      }
      status = Status.SUCCEEDED;
      paidAt = result.paidAt();
      changed = true;
    } else if (status == Status.PENDING
        && (result.state() == PaymentGateway.State.CLOSED
            || (result.state() == PaymentGateway.State.NOT_FOUND
                && !now.isBefore(expiresAt.plusSeconds(120))))) {
      status = Status.CLOSED;
      changed = true;
    }
    lastFailure = null;
    schedule(now);
    return changed;
  }

  private void schedule(Instant now) {
    nextAttemptAt =
        switch (status) {
          case SUCCEEDED -> null;
          case PENDING -> now.plusSeconds(30);
          case CLOSED -> now.isBefore(expiresAt.plusSeconds(86400)) ? now.plusSeconds(600) : null;
        };
  }

  /** 构建不含凭证的渠道请求快照. */
  public PaymentGateway.TradeRequest request() {
    return new PaymentGateway.TradeRequest(id, amount, expiresAt);
  }

  /** 已付款不可被延迟到达的关闭或等待通知覆盖. */
  public enum Status {
    PENDING,
    SUCCEEDED,
    CLOSED
  }

  /** 返回支付单标识. */
  public UUID id() {
    return id;
  }

  /** 返回业务引用. */
  public UUID businessRef() {
    return businessRef;
  }

  /** 返回顾客标识. */
  public UUID customerId() {
    return customerId;
  }

  /** 返回人民币支付金额. */
  public BigDecimal amount() {
    return amount;
  }

  /** 返回顾客提交幂等键. */
  public String idempotencyKey() {
    return idempotencyKey;
  }

  /** 返回请求摘要. */
  public String fingerprint() {
    return fingerprint;
  }

  /** 返回创建时间. */
  public Instant createdAt() {
    return createdAt;
  }

  /** 返回不可延长的过期时刻. */
  public Instant expiresAt() {
    return expiresAt;
  }

  /** 返回已确认支付状态. */
  public Status status() {
    return status;
  }

  /** 返回渠道交易号. */
  public String tradeNo() {
    return tradeNo;
  }

  /** 返回渠道确认的付款时间. */
  public Instant paidAt() {
    return paidAt;
  }

  /** 返回是否要求关闭支付. */
  public boolean closeRequested() {
    return closeRequested;
  }

  /** 返回下次对账或领取到期时间. */
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
