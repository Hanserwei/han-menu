package com.hanserwei.hanmenu.payment.application;

import com.hanserwei.hanmenu.payment.domain.PaymentGateway;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 渠道工作器拒绝数据库事务包裹，崩溃后的持久化任务可重复领取. */
@Service
@Transactional(propagation = Propagation.NEVER)
public class PaymentReconciliation {
  private final PaymentTransactions transactions;
  private final PaymentGateway gateway;
  private final Clock clock;

  /** 注入独立短事务服务，避免自调用使事务边界失效. */
  public PaymentReconciliation(
      PaymentTransactions transactions, PaymentGateway gateway, Clock clock) {
    this.transactions = transactions;
    this.gateway = gateway;
    this.clock = clock;
  }

  /** 有界处理支付及退款任务，单项失败不丢失其他待处理记录. */
  public void reconcile() {
    for (UUID id : transactions.duePayments()) {
      payment(id);
    }
    for (UUID id : transactions.dueRefunds()) {
      refund(id);
    }
  }

  /** 先查单，再按固定过期时间或关闭意图关单；不能用 App 返回值推进状态. */
  public void payment(UUID id) {
    var claimed = transactions.claimPayment(id);
    if (claimed.isEmpty()) {
      return;
    }
    var payment = claimed.orElseThrow();
    try {
      var result = gateway.query(payment.request());
      if (result.state() == PaymentGateway.State.PENDING
          && (payment.closeRequested() || !clock.instant().isBefore(payment.expiresAt()))) {
        result = gateway.close(payment.request());
      }
      transactions.observe(id, result);
    } catch (RuntimeException exception) {
      // 渠道异常可能含密钥或原始报文；只保存稳定分类，由原意图支持后续恢复。
      transactions.paymentFailed(id);
    }
  }

  /** 发起和查询使用固定退款号；丢失响应后优先查结果，再安全重试同一退款. */
  public void refund(UUID id) {
    var claimed = transactions.claimRefund(id);
    if (claimed.isEmpty()) {
      return;
    }
    var refund = claimed.orElseThrow();
    try {
      if (!gateway.refundSucceeded(refund.request())) {
        gateway.refund(refund.request());
      }
      if (gateway.refundSucceeded(refund.request())) {
        transactions.refundConfirmed(id);
      } else {
        transactions.refundFailed(id);
      }
    } catch (RuntimeException exception) {
      transactions.refundFailed(id);
    }
  }
}
