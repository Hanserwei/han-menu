package com.hanserwei.hanmenu.reporting.application;

import com.hanserwei.hanmenu.customer.events.CustomerRegistered;
import com.hanserwei.hanmenu.ordering.events.OrderChanged;
import com.hanserwei.hanmenu.payment.events.PaymentResult;
import com.hanserwei.hanmenu.payment.events.RefundResult;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** 统计事件在各自可靠事务内幂等应用，失败时由现有 Modulith 恢复任务重投. */
@Component
class ReportingEventListener {
  private final ReportProjector projector;

  ReportingEventListener(ReportProjector projector) {
    this.projector = projector;
  }

  @ApplicationModuleListener
  public void order(OrderChanged event) {
    projector.order(event.snapshot());
  }

  @ApplicationModuleListener
  public void customer(CustomerRegistered event) {
    projector.customer(event.customerId(), event.createdAt());
  }

  @ApplicationModuleListener
  public void payment(PaymentResult event) {
    if (event.status().equals("SUCCEEDED")) {
      projector.receipt(event.paymentId(), event.businessRef(), event.amount(), event.paidAt());
    }
  }

  @ApplicationModuleListener
  public void refund(RefundResult event) {
    projector.refund(
        event.refundId(),
        event.paymentId(),
        event.businessRef(),
        event.amount(),
        event.confirmedAt());
  }
}
