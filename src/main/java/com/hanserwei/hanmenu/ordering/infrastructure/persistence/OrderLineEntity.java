package com.hanserwei.hanmenu.ordering.infrastructure.persistence;

import com.hanserwei.hanmenu.ordering.domain.OrderLine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 只在创建订单时写入的条目快照实体，没有更新业务入口. */
@Entity(name = "OrderLineSnapshot")
@Table(name = "ordering_line")
public class OrderLineEntity {
  @Id UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  OrderEntity order;

  int position;
  UUID productId;
  String kind;
  String name;

  @Column(precision = 12, scale = 2)
  BigDecimal unitPrice;

  int quantity;

  @JdbcTypeCode(SqlTypes.JSON)
  Map<String, String> selections;

  @JdbcTypeCode(SqlTypes.JSON)
  List<ComponentValue> components;

  /** ORM 重建入口. */
  protected OrderLineEntity() {}

  static OrderLineEntity from(OrderEntity order, int position, OrderLine value) {
    var entity = new OrderLineEntity();
    entity.order = order;
    entity.position = position;
    entity.id = value.id();
    entity.productId = value.productId();
    entity.kind = value.kind();
    entity.name = value.name();
    entity.unitPrice = value.unitPrice();
    entity.quantity = value.quantity();
    entity.selections = value.selections();
    entity.components =
        value.components().stream()
            .map(
                component ->
                    new ComponentValue(
                        component.productId(),
                        component.name(),
                        component.quantity(),
                        component.selections()))
            .toList();
    return entity;
  }

  OrderLine domain() {
    return new OrderLine(
        id,
        productId,
        kind,
        name,
        unitPrice,
        quantity,
        selections,
        components.stream()
            .map(
                value ->
                    new OrderLine.Component(
                        value.productId(), value.name(), value.quantity(), value.selections()))
            .toList());
  }

  /** 套餐 JSON 持久化模型，不直接序列化领域类型. */
  public record ComponentValue(
      UUID productId, String name, int quantity, Map<String, String> selections) {}
}
