package com.hanserwei.hanmenu.ordering.application;

import com.hanserwei.hanmenu.customer.api.CustomerIdentity;
import com.hanserwei.hanmenu.payment.api.PaymentOperations;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 在订单意图事务结束后生成 App 参数，渠道能力不能包在订单长事务中. */
@Service
@Transactional(propagation = Propagation.NEVER)
public class OrderPaymentService {
  private final OrderLifecycleService lifecycle;
  private final PaymentOperations payments;

  /** 组合短事务登记与外部支付契约. */
  public OrderPaymentService(OrderLifecycleService lifecycle, PaymentOperations payments) {
    this.lifecycle = lifecycle;
    this.payments = payments;
  }

  /** 服务端取订单金额，客户端只提交订单版本及幂等键. */
  public Creation create(CustomerIdentity identity, UUID id, String key, long version) {
    payments.requireConfigured();
    var intent = lifecycle.reserve(identity, id, key, version);
    return new Creation(payments.parameters(intent.id(), identity.customerId()), intent.replayed());
  }

  /** 资源创建结果保留是否重放，HTTP 可区分首次创建与同键重试. */
  public record Creation(PaymentOperations.AppPayment payment, boolean replayed) {}
}
