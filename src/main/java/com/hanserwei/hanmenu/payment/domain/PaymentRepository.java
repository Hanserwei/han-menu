package com.hanserwei.hanmenu.payment.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 支付和退款仓储端口，所有写操作参与调用方短事务. */
public interface PaymentRepository {
  /** 按顾客和幂等键读取原提交. */
  Optional<Payment> submitted(UUID customerId, String key);

  /** 每个业务订单最多一个支付意图. */
  Optional<Payment> forBusiness(UUID businessRef);

  /** 查询支付单. */
  Optional<Payment> payment(UUID id);

  /** 锁定支付行，串行应用通知、查询和取消意图. */
  Payment lockPayment(UUID id);

  /** 保存新的支付单. */
  void add(Payment payment);

  /** 保存聚合变更，执行版本校验. */
  void update(Payment payment);

  /** 查询某个支付已登记的唯一全额退款. */
  Optional<Refund> refundForPayment(UUID paymentId);

  /** 读取退款单. */
  Optional<Refund> refund(UUID id);

  /** 锁定退款单，重复回执只完成一次. */
  Refund lockRefund(UUID id);

  /** 登记唯一全额退款意图. */
  void addRefund(Refund refund);

  /** 保存退款状态. */
  void updateRefund(Refund refund);

  /** 按到期时间取得有界待处理支付标识. */
  List<UUID> duePayments(Instant now, int limit);

  /** 按到期时间取得有界待处理退款标识. */
  List<UUID> dueRefunds(Instant now, int limit);

  /** 分批导出已确认支付，统计模块不得直接查询支付表. */
  List<Payment> receiptsAfter(UUID cursor, int limit);

  /** 分批导出已确认退款. */
  List<Refund> refundsAfter(UUID cursor, int limit);
}
