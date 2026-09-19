package com.hanserwei.hanmenu.payment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 订单使用的支付能力，业务引用保持不透明，支付模块不反向依赖订单. */
public interface PaymentOperations {
  /** 预检本地渠道配置，在建单事务之前失败而非创建无效支付参数. */
  void requireConfigured();

  /** 在订单事务中登记幂等支付意图，调用方必须先锁定业务订单. */
  Intent reserve(
      UUID businessRef,
      UUID customerId,
      BigDecimal amount,
      Instant expiresAt,
      String key,
      long orderVersion);

  /** 返回已登记的支付意图，仅用于同键重试；不同请求内容仍须由 reserve 拒绝. */
  boolean submitted(UUID customerId, String key);

  /** 持久化关单或全额退款意图，不在事务中调用渠道. */
  void stop(UUID paymentId);

  /** 为取消后迟到的真实付款登记唯一全额退款. */
  void refund(UUID paymentId);

  /** 在订单事务结束后签发可用 App 参数，再次检查支付可用状态. */
  AppPayment parameters(UUID paymentId, UUID customerId);

  /** 订单持有的最小支付引用. */
  record Intent(UUID id, String status, Instant expiresAt, boolean replayed) {}

  /** App 只拿到沙箱签名参数，参数本身不能证明付款成功. */
  record AppPayment(
      UUID id,
      String status,
      BigDecimal amount,
      String currency,
      Instant expiresAt,
      long version,
      String channel,
      String invocation,
      String orderString) {
    @Override
    public String toString() {
      return "AppPayment[id=" + id + ", 签名参数已隐藏]";
    }
  }
}
