package com.hanserwei.hanmenu.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** 支付渠道端口；远程查询、关单及退款只能在数据库事务之外调用. */
public interface PaymentGateway {
  /** 本地生成 App 支付签名参数，不创建假成功交易. */
  String appParameters(TradeRequest request);

  /** 主动查单；无法确认时抛出依赖错误，不能降级为成功或关闭. */
  TradeResult query(TradeRequest request);

  /** 关闭真实待付款交易，结果不明确时由后续查单恢复. */
  TradeResult close(TradeRequest request);

  /** 使用固定退款号发起全额退款，受理后仍需独立查询确认. */
  void refund(RefundRequest request);

  /** 查询退款最终结果，返回成功前校验原交易、退款号与金额. */
  boolean refundSucceeded(RefundRequest request);

  /** 校验签名、应用和收款方，并转换为领域可验证的通知事实. */
  Notice verify(Map<String, String> parameters);

  /** 预检沙箱配置完整性，不发起网络请求. */
  void requireConfigured();

  /** 渠道请求只含内部交易引用、人民币金额和固定过期时刻. */
  record TradeRequest(UUID paymentId, BigDecimal amount, Instant expiresAt) {}

  /** 全额退款请求使用稳定业务退款号，网络重试不能重复退款. */
  record RefundRequest(UUID refundId, UUID paymentId, String tradeNo, BigDecimal amount) {}

  /** 已验证的渠道交易事实，不带 SDK 对象. */
  record TradeResult(State state, String tradeNo, BigDecimal amount, Instant paidAt) {}

  /** 验签后的通知仍须与数据库支付单的金额和渠道号核对. */
  record Notice(UUID paymentId, TradeResult result) {}

  /** 不存在不是支付已关闭，签名参数仍可能在有效期内创建交易. */
  enum State {
    NOT_FOUND,
    PENDING,
    SUCCEEDED,
    CLOSED
  }
}
