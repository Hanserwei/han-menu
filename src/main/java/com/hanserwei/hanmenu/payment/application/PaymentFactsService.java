package com.hanserwei.hanmenu.payment.application;

import com.hanserwei.hanmenu.payment.api.PaymentFacts;
import com.hanserwei.hanmenu.payment.domain.PaymentRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 只从已确认支付和退款读取资金事实，参与调用方一致性快照事务. */
@Service
@Transactional(readOnly = true)
class PaymentFactsService implements PaymentFacts {
  private final PaymentRepository repository;

  PaymentFactsService(PaymentRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<Receipt> receiptsAfter(UUID cursor, int limit) {
    bound(limit);
    return repository.receiptsAfter(cursor, limit).stream()
        .map(value -> new Receipt(value.id(), value.businessRef(), value.amount(), value.paidAt()))
        .toList();
  }

  @Override
  public List<Refund> refundsAfter(UUID cursor, int limit) {
    bound(limit);
    return repository.refundsAfter(cursor, limit).stream()
        .map(
            value ->
                new Refund(
                    value.id(),
                    value.paymentId(),
                    value.businessRef(),
                    value.amount(),
                    value.confirmedAt()))
        .toList();
  }

  private void bound(int limit) {
    if (limit < 1 || limit > 200) {
      throw new IllegalArgumentException("统计批次须为 1 至 200");
    }
  }
}
