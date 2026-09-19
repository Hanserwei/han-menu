package com.hanserwei.hanmenu.reporting.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 统计模块自己的不可变事实模型，不能引用其他模块聚合或 ORM 实体. */
public final class ReportingFacts {
  private ReportingFacts() {}

  /** 订单投影按源版本更新，历史明细和金额不由统计模块重新定价. */
  public record Order(
      UUID id,
      UUID customerId,
      long sourceVersion,
      String status,
      BigDecimal total,
      Instant createdAt,
      Instant paidAt,
      Instant completedAt,
      Instant cancelledAt,
      UUID paymentId,
      String refundStatus,
      UUID refundId,
      List<Line> lines) {
    /** 固定快照、金额和时间精度. */
    public Order {
      Objects.requireNonNull(id);
      Objects.requireNonNull(customerId);
      Objects.requireNonNull(status);
      Objects.requireNonNull(createdAt);
      if (sourceVersion < 0 || lines.isEmpty() || lines.size() > 50) {
        throw new IllegalArgumentException("订单统计快照不合法");
      }
      total = money(total);
      createdAt = BusinessTime.canonical(createdAt);
      paidAt = BusinessTime.canonical(paidAt);
      completedAt = BusinessTime.canonical(completedAt);
      cancelledAt = BusinessTime.canonical(cancelledAt);
      lines = List.copyOf(lines);
    }

    /** 重复和较旧事件不得覆盖已经推进的投影. */
    public boolean newerThan(long previousVersion) {
      return sourceVersion > previousVersion;
    }
  }

  /** 商品销量按稳定标识合并，单价来自成交快照. */
  public record Line(
      UUID id, UUID productId, String name, String kind, int quantity, BigDecimal unitPrice) {
    /** 固定成交金额并验证数量. */
    public Line {
      Objects.requireNonNull(id);
      Objects.requireNonNull(productId);
      Objects.requireNonNull(name);
      Objects.requireNonNull(kind);
      if (quantity < 1 || quantity > 99) {
        throw new IllegalArgumentException("销量快照数量不合法");
      }
      unitPrice = money(unitPrice);
    }

    /** 精确计算成交小计. */
    public BigDecimal subtotal() {
      return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
  }

  /** 仅保留注册编号与时间，增长统计不保存顾客个人资料. */
  public record Customer(UUID id, Instant createdAt) {
    /** 固定已注册事实的时间精度. */
    public Customer {
      Objects.requireNonNull(id);
      createdAt = BusinessTime.canonical(Objects.requireNonNull(createdAt));
    }
  }

  /** 已确认收款，重复事件不能重复增加营业资金. */
  public record Receipt(UUID id, UUID orderId, BigDecimal amount, Instant paidAt) {
    /** 校验收款事实并统一精度. */
    public Receipt {
      Objects.requireNonNull(id);
      Objects.requireNonNull(orderId);
      amount = money(amount);
      paidAt = BusinessTime.canonical(Objects.requireNonNull(paidAt));
    }
  }

  /** 已确认退款，受理或重试状态不形成资金支出. */
  public record Refund(
      UUID id, UUID paymentId, UUID orderId, BigDecimal amount, Instant confirmedAt) {
    /** 校验原支付引用和退款事实. */
    public Refund {
      Objects.requireNonNull(id);
      Objects.requireNonNull(paymentId);
      Objects.requireNonNull(orderId);
      amount = money(amount);
      confirmedAt = BusinessTime.canonical(Objects.requireNonNull(confirmedAt));
    }
  }

  private static BigDecimal money(BigDecimal value) {
    if (value == null || value.signum() <= 0) {
      throw new IllegalArgumentException("统计金额必须为正");
    }
    return value.setScale(2, RoundingMode.UNNECESSARY);
  }
}
