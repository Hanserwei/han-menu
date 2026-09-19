package com.hanserwei.hanmenu.ordering.application;

import com.hanserwei.hanmenu.ordering.domain.Order;
import com.hanserwei.hanmenu.ordering.domain.OrderPage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 集中映射订单 HTTP 查询模型，避免暴露聚合或持久化对象. */
public final class OrderViews {
  private OrderViews() {}

  /** 将历史快照映射为详情，不读取实时目录或地址簿. */
  public static Detail detail(Order order) {
    var address = order.address();
    return new Detail(
        order.id(),
        order.status().name(),
        order.version(),
        order.createdAt(),
        order.cancelledAt(),
        new Address(
            address.sourceId(),
            address.sourceVersion(),
            address.recipientName(),
            address.phone(),
            address.province(),
            address.city(),
            address.district(),
            address.detail()),
        order.lines().stream()
            .map(
                line ->
                    new Line(
                        line.id(),
                        line.productId(),
                        line.kind(),
                        line.name(),
                        line.unitPrice(),
                        line.quantity(),
                        line.selections(),
                        line.components().stream()
                            .map(
                                value ->
                                    new Component(
                                        value.productId(),
                                        value.name(),
                                        value.quantity(),
                                        value.selections()))
                            .toList(),
                        line.subtotal()))
            .toList(),
        order.total(),
        "CNY",
        order.expiresAt(),
        new Lifecycle(
            order.lifecycle().paymentId(),
            order.lifecycle().paidAt(),
            order.lifecycle().acceptedAt(),
            order.lifecycle().deliveredAt(),
            order.lifecycle().completedAt(),
            order.lifecycle().cancelReason() == null
                ? null
                : order.lifecycle().cancelReason().name(),
            order.lifecycle().refundStatus().name(),
            order.lifecycle().refundId()),
        order.reminderCount(),
        order.lastRemindedAt());
  }

  /** 映射有界历史查询，稳定排序由仓储完成. */
  public static History history(OrderPage page, int number, int size) {
    return new History(
        page.items().stream()
            .map(
                value ->
                    new Summary(
                        value.id(),
                        value.status().name(),
                        value.total(),
                        "CNY",
                        value.version(),
                        value.createdAt(),
                        value.cancelledAt()))
            .toList(),
        number,
        size,
        page.totalElements(),
        Math.ceilDiv(page.totalElements(), size));
  }

  /** 订单详情仅向所属顾客返回收货资料. */
  public record Detail(
      UUID id,
      String status,
      long version,
      Instant createdAt,
      Instant cancelledAt,
      Address address,
      List<Line> items,
      BigDecimal total,
      String currency,
      Instant expiresAt,
      Lifecycle lifecycle,
      int reminderCount,
      Instant lastRemindedAt) {
    /** 固定条目列表. */
    public Detail {
      items = List.copyOf(items);
    }
  }

  /** 生命周期查询 DTO 不暴露领域对象，取消与退款结果分开表达. */
  public record Lifecycle(
      UUID paymentId,
      Instant paidAt,
      Instant acceptedAt,
      Instant deliveredAt,
      Instant completedAt,
      String cancelReason,
      String refundStatus,
      UUID refundId) {}

  /** 收货地址快照不在调试字符串中暴露个人信息. */
  public record Address(
      UUID sourceId,
      long sourceVersion,
      String recipientName,
      String phone,
      String province,
      String city,
      String district,
      String detail) {
    @Override
    public String toString() {
      return "Address[收货资料已隐藏]";
    }
  }

  /** 成交条目金额不随目录调价变化. */
  public record Line(
      UUID id,
      UUID productId,
      String kind,
      String name,
      BigDecimal unitPrice,
      int quantity,
      Map<String, String> selections,
      List<Component> components,
      BigDecimal subtotal) {
    /** 固定规格与组成. */
    public Line {
      selections = Map.copyOf(selections);
      components = List.copyOf(components);
    }
  }

  /** 套餐组成保留成交时菜品名称与规格. */
  public record Component(
      UUID productId, String name, int quantity, Map<String, String> selections) {
    /** 固定组成规格. */
    public Component {
      selections = Map.copyOf(selections);
    }
  }

  /** 历史列表摘要不包含顾客个人资料. */
  public record Summary(
      UUID id,
      String status,
      BigDecimal total,
      String currency,
      long version,
      Instant createdAt,
      Instant cancelledAt) {}

  /** 零基分页模型，限制单页最大五十条. */
  public record History(
      List<Summary> items, int page, int size, long totalElements, long totalPages) {
    /** 固定分页集合. */
    public History {
      items = List.copyOf(items);
    }
  }
}
