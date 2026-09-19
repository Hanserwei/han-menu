package com.hanserwei.hanmenu.ordering.infrastructure.persistence;

import com.hanserwei.hanmenu.ordering.domain.Order;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 订单实体仅在创建时复制历史快照，生命周期更新只写付款、取消、退款及履约事实. */
@Entity(name = "CustomerOrder")
@Table(name = "ordering_order")
public class OrderEntity {
  @Id UUID id;
  UUID customerId;
  String idempotencyKey;
  String requestFingerprint;
  @Embedded AddressValue address;

  @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST)
  @OrderBy("position ASC")
  List<OrderLineEntity> lines = new ArrayList<>();

  @Column(precision = 16, scale = 2)
  BigDecimal total;

  @Enumerated(EnumType.STRING)
  Order.Status status;

  @Version Long version;
  Instant createdAt;
  Instant cancelledAt;
  UUID paymentId;
  Instant paidAt;
  Instant acceptedAt;
  Instant deliveredAt;
  Instant completedAt;

  @Enumerated(EnumType.STRING)
  Order.CancelReason cancelReason;

  @Enumerated(EnumType.STRING)
  Order.RefundStatus refundStatus;

  UUID refundId;
  int reminderCount;
  Instant lastRemindedAt;

  /** ORM 重建入口. */
  protected OrderEntity() {}

  static OrderEntity from(Order value) {
    var entity = new OrderEntity();
    entity.id = value.id();
    entity.customerId = value.customerId();
    entity.idempotencyKey = value.idempotencyKey();
    entity.requestFingerprint = value.requestFingerprint();
    entity.address = AddressValue.from(value.address());
    for (int i = 0; i < value.lines().size(); i++) {
      entity.lines.add(OrderLineEntity.from(entity, i, value.lines().get(i)));
    }
    entity.total = value.total();
    entity.createdAt = value.createdAt();
    entity.applyState(value);
    return entity;
  }

  void applyState(Order value) {
    status = value.status();
    cancelledAt = value.cancelledAt();
    var lifecycle = value.lifecycle();
    paymentId = lifecycle.paymentId();
    paidAt = lifecycle.paidAt();
    acceptedAt = lifecycle.acceptedAt();
    deliveredAt = lifecycle.deliveredAt();
    completedAt = lifecycle.completedAt();
    cancelReason = lifecycle.cancelReason();
    refundStatus = lifecycle.refundStatus();
    refundId = lifecycle.refundId();
    reminderCount = value.reminderCount();
    lastRemindedAt = value.lastRemindedAt();
  }

  Order domain() {
    var value =
        new Order(
            id,
            customerId,
            idempotencyKey,
            requestFingerprint,
            address.domain(),
            lines.stream().map(OrderLineEntity::domain).toList(),
            status,
            version,
            createdAt,
            cancelledAt,
            new Order.Lifecycle(
                paymentId,
                paidAt,
                acceptedAt,
                deliveredAt,
                completedAt,
                cancelReason,
                refundStatus,
                refundId),
            reminderCount,
            lastRemindedAt);
    if (value.total().compareTo(total) != 0) {
      throw new IllegalStateException("订单金额与条目不一致");
    }
    return value;
  }
}
