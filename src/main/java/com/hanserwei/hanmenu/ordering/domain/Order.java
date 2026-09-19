package com.hanserwei.hanmenu.ordering.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 订单聚合只允许待付款转为已取消，金额和收货快照终身不变. */
public final class Order {
  private final UUID id;
  private final UUID customerId;
  private final String idempotencyKey;
  private final String requestFingerprint;
  private final AddressSnapshot address;
  private final List<OrderLine> lines;
  private final BigDecimal total;
  private final long version;
  private final Instant createdAt;
  private Status status;
  private Instant cancelledAt;

  /** 重建持久化快照并核对状态、金额及时间的一致性. */
  public Order(
      UUID id,
      UUID customerId,
      String idempotencyKey,
      String requestFingerprint,
      AddressSnapshot address,
      List<OrderLine> lines,
      Status status,
      long version,
      Instant createdAt,
      Instant cancelledAt) {
    this.id = Objects.requireNonNull(id);
    this.customerId = Objects.requireNonNull(customerId);
    this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
    this.requestFingerprint = Objects.requireNonNull(requestFingerprint);
    this.address = Objects.requireNonNull(address);
    this.lines = List.copyOf(lines);
    this.status = Objects.requireNonNull(status);
    this.version = version;
    this.createdAt = Objects.requireNonNull(createdAt);
    this.cancelledAt = cancelledAt;
    if (lines.isEmpty()
        || lines.size() > 50
        || version < 0
        || lines.stream().map(OrderLine::id).distinct().count() != lines.size()
        || (status == Status.CANCELLED) != (cancelledAt != null)
        || (cancelledAt != null && cancelledAt.isBefore(createdAt))) {
      throw new OrderException(OrderException.Reason.INVALID_INPUT, "订单快照不合法");
    }
    this.total =
        lines.stream().map(OrderLine::subtotal).reduce(new BigDecimal("0.00"), BigDecimal::add);
  }

  /** 新建待付款订单，服务端只接受已重新校验的快照. */
  public static Order submit(
      UUID customerId,
      String key,
      String fingerprint,
      AddressSnapshot address,
      List<OrderLine> lines,
      Instant now) {
    return new Order(
        UUID.randomUUID(),
        customerId,
        key,
        fingerprint,
        address,
        lines,
        Status.UNPAID,
        0,
        now,
        null);
  }

  /** 校验客户端版本，拒绝陈旧修改. */
  public void requireVersion(long expected) {
    if (expected != version) {
      throw new OrderException(OrderException.Reason.VERSION_CONFLICT, "订单已被修改");
    }
  }

  /** 同键必须对应相同规范化请求，已取消订单也不能复用该键重新建单. */
  public void requireSameRequest(String fingerprint) {
    if (!requestFingerprint.equals(fingerprint)) {
      throw new OrderException(OrderException.Reason.IDEMPOTENCY_CONFLICT, "幂等键已用于其他请求");
    }
  }

  /** 取消待付款订单；不自动恢复购物车，避免覆盖顾客后续选择. */
  public void cancel(long expectedVersion, Instant now) {
    requireVersion(expectedVersion);
    if (status != Status.UNPAID) {
      throw new OrderException(OrderException.Reason.STATE_CONFLICT, "只有待付款订单可以取消");
    }
    if (now.isBefore(createdAt)) {
      throw new IllegalArgumentException("取消时间早于订单创建时间");
    }
    status = Status.CANCELLED;
    cancelledAt = Objects.requireNonNull(now);
  }

  /** 本阶段没有支付成功或履约状态，后续必须由真实支付事实驱动. */
  public enum Status {
    UNPAID,
    CANCELLED
  }

  /** 返回订单标识. */
  public UUID id() {
    return id;
  }

  /** 返回顾客标识. */
  public UUID customerId() {
    return customerId;
  }

  /** 返回提交幂等键. */
  public String idempotencyKey() {
    return idempotencyKey;
  }

  /** 返回规范化请求摘要. */
  public String requestFingerprint() {
    return requestFingerprint;
  }

  /** 返回不可变地址快照. */
  public AddressSnapshot address() {
    return address;
  }

  /** 返回不可变条目快照. */
  public List<OrderLine> lines() {
    return lines;
  }

  /** 返回人民币总金额. */
  public BigDecimal total() {
    return total;
  }

  /** 返回持久化版本. */
  public long version() {
    return version;
  }

  /** 返回创建时刻. */
  public Instant createdAt() {
    return createdAt;
  }

  /** 返回订单状态. */
  public Status status() {
    return status;
  }

  /** 返回取消时刻. */
  public Instant cancelledAt() {
    return cancelledAt;
  }
}
