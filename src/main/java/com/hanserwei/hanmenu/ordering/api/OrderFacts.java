package com.hanserwei.hanmenu.ordering.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 统计专用的无个人资料订单快照，重建通过业务 API 读取而非访问订单表. */
public interface OrderFacts {
  /** 按数据库 UUID 顺序进行键集分页，每批最多二百条；跨批一致性由调用方只读事务保证. */
  List<Snapshot> after(UUID cursor, int limit);

  /** 订单的版本化统计事实，不含收货信息、令牌或客户端价格. */
  record Snapshot(
      UUID id,
      UUID customerId,
      long version,
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
    /** 固定成交明细快照. */
    public Snapshot {
      lines = List.copyOf(lines);
    }
  }

  /** 销量统计只需要商品标识、历史名称、数量与成交单价. */
  record Line(
      UUID id, UUID productId, String name, String kind, int quantity, BigDecimal unitPrice) {}
}
