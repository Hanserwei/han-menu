package com.hanserwei.hanmenu.reporting.application;

import com.hanserwei.hanmenu.ordering.api.OrderFacts;
import com.hanserwei.hanmenu.reporting.domain.ReportingFacts;
import com.hanserwei.hanmenu.reporting.domain.ReportingRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 每次事件应用先锁投影控制行，与原子重建串行，旧版本不能覆盖新统计. */
@Service
@Transactional
public class ReportProjector {
  private final ReportingRepository repository;
  private final Clock clock;

  /** 组合本模块投影仓储与业务时钟. */
  public ReportProjector(ReportingRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  /** 按源订单版本应用完整快照，重复事件不重复累计销量. */
  public void order(OrderFacts.Snapshot value) {
    var fact = FactMapper.order(value);
    apply(() -> repository.order(fact));
  }

  /** 按注册编号去重，顾客手机号或昵称不会进入投影. */
  public void customer(UUID id, Instant createdAt) {
    var fact = new ReportingFacts.Customer(id, createdAt);
    apply(() -> repository.customer(fact));
  }

  /** 按支付单编号去重已确认收款，关闭事件不形成收款. */
  public void receipt(UUID id, UUID orderId, BigDecimal amount, Instant paidAt) {
    var fact = new ReportingFacts.Receipt(id, orderId, amount, paidAt);
    apply(() -> repository.receipt(fact));
  }

  /** 按退款编号去重已确认支出，允许退款事实先于收款事件到达. */
  public void refund(
      UUID id, UUID paymentId, UUID orderId, BigDecimal amount, Instant confirmedAt) {
    var fact = new ReportingFacts.Refund(id, paymentId, orderId, amount, confirmedAt);
    apply(() -> repository.refund(fact));
  }

  private void apply(BooleanSupplier action) {
    var state = repository.lock();
    if (action.getAsBoolean()) {
      state.applied(clock.instant());
      repository.saveState(state);
    }
  }
}
