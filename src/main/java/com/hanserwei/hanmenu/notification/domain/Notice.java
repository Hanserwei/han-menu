package com.hanserwei.hanmenu.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 持久化通知聚合维护有界重试，客户端仍通过提交游标补查并确认阅读. */
public final class Notice {
  private final UUID id;
  private final long sequence;
  private final UUID orderId;
  private final Kind kind;
  private final Instant occurredAt;
  private final Instant createdAt;
  private Status status;
  private int attempts;
  private int failures;
  private Instant nextAttemptAt;
  private UUID activeAttemptId;
  private String lastFailure;
  private final long version;

  /** 从持久化快照重建消息投递状态. */
  public Notice(
      UUID id,
      long sequence,
      UUID orderId,
      Kind kind,
      Instant occurredAt,
      Instant createdAt,
      Status status,
      int attempts,
      int failures,
      Instant nextAttemptAt,
      UUID activeAttemptId,
      String lastFailure,
      long version) {
    this.id = id;
    this.sequence = sequence;
    this.orderId = orderId;
    this.kind = kind;
    this.occurredAt = occurredAt;
    this.createdAt = createdAt;
    this.status = status;
    this.attempts = attempts;
    this.failures = failures;
    this.nextAttemptAt = nextAttemptAt;
    this.activeAttemptId = activeAttemptId;
    this.lastFailure = lastFailure;
    this.version = version;
    Objects.requireNonNull(id);
    Objects.requireNonNull(orderId);
    Objects.requireNonNull(kind);
    Objects.requireNonNull(occurredAt);
    Objects.requireNonNull(createdAt);
    Objects.requireNonNull(status);
    if (sequence < 1 || attempts < 0 || failures < 0 || version < 0) {
      throw new IllegalArgumentException("通知快照不合法");
    }
  }

  /** 为新业务事件建立可恢复消息，不把浏览器在线作为持久化前提. */
  public static Notice create(
      UUID id, long sequence, UUID orderId, Kind kind, Instant occurredAt, Instant now) {
    return new Notice(
        id, sequence, orderId, kind, occurredAt, now, Status.PENDING, 0, 0, now, null, null, 0);
  }

  /** 在短事务中领取投递令牌；崩溃后到期可重新领取，旧令牌不能覆盖新状态. */
  public UUID claim(Instant now) {
    if (nextAttemptAt == null
        || nextAttemptAt.isAfter(now)
        || status == Status.DELIVERED
        || status == Status.EXHAUSTED) {
      return null;
    }
    status = Status.IN_FLIGHT;
    attempts = Math.incrementExact(attempts);
    activeAttemptId = UUID.randomUUID();
    nextAttemptAt = now.plusSeconds(60);
    return activeAttemptId;
  }

  /** 只接受当前领取的发送结果，成功只代表写入在线连接，不代表员工已阅读. */
  public boolean finish(UUID attemptId, boolean success, String failure, Instant now) {
    if (status != Status.IN_FLIGHT || !Objects.equals(activeAttemptId, attemptId)) {
      return false;
    }
    activeAttemptId = null;
    if (success) {
      status = Status.DELIVERED;
      nextAttemptAt = null;
      lastFailure = null;
      failures = 0;
    } else {
      failures++;
      lastFailure = failure;
      status = failures >= 5 ? Status.EXHAUSTED : Status.PENDING;
      nextAttemptAt =
          status == Status.EXHAUSTED ? null : now.plusSeconds(Math.min(60, 5L << (failures - 1)));
    }
    return true;
  }

  /** 管理员可按版本重新发起已耗尽的实时提示，历史消息及累计尝试数保持不变. */
  public void retry(long expected, Instant now) {
    if (version != expected) {
      throw new NotificationException(NotificationException.Reason.VERSION_CONFLICT, "通知已更新");
    }
    if (status != Status.EXHAUSTED) {
      throw new NotificationException(NotificationException.Reason.CONFLICT, "只有重试耗尽的通知可以重新投递");
    }
    status = Status.PENDING;
    failures = 0;
    nextAttemptAt = now;
    lastFailure = null;
  }

  /** 固定通知类型，不保存用户输入的消息文本或收货信息. */
  public enum Kind {
    NEW_ORDER,
    ORDER_REMINDER
  }

  /** 投递状态与员工阅读确认独立，耗尽不会删除可补查历史. */
  public enum Status {
    PENDING,
    IN_FLIGHT,
    DELIVERED,
    EXHAUSTED
  }

  /** 返回消息标识. */
  public UUID id() {
    return id;
  }

  /** 返回提交顺序游标. */
  public long sequence() {
    return sequence;
  }

  /** 返回业务订单标识. */
  public UUID orderId() {
    return orderId;
  }

  /** 返回消息种类. */
  public Kind kind() {
    return kind;
  }

  /** 返回业务发生时刻. */
  public Instant occurredAt() {
    return occurredAt;
  }

  /** 返回消息持久化时间. */
  public Instant createdAt() {
    return createdAt;
  }

  /** 返回实时投递状态. */
  public Status status() {
    return status;
  }

  /** 返回累计投递尝试数. */
  public int attempts() {
    return attempts;
  }

  /** 返回本轮连续失败数. */
  public int failures() {
    return failures;
  }

  /** 返回重试或领取到期时刻. */
  public Instant nextAttemptAt() {
    return nextAttemptAt;
  }

  /** 返回当前投递令牌. */
  public UUID activeAttemptId() {
    return activeAttemptId;
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
