package com.hanserwei.hanmenu.ordering.application;

import com.hanserwei.hanmenu.payment.events.PaymentResult;
import com.hanserwei.hanmenu.payment.events.RefundResult;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** 可靠消费支付结果；独立事务失败时保留 Modulith 登记供恢复任务重投. */
@Component
class PaymentResultListener {
  private final OrderLifecycleService lifecycle;

  PaymentResultListener(OrderLifecycleService lifecycle) {
    this.lifecycle = lifecycle;
  }

  @ApplicationModuleListener
  public void payment(PaymentResult result) {
    lifecycle.paymentResult(result);
  }

  @ApplicationModuleListener
  public void refund(RefundResult result) {
    lifecycle.refundResult(result);
  }
}
