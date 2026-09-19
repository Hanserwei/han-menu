package com.hanserwei.hanmenu.reporting.application;

import com.hanserwei.hanmenu.customer.api.CustomerFacts;
import com.hanserwei.hanmenu.identity.api.StaffAuthorization;
import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import com.hanserwei.hanmenu.ordering.api.OrderFacts;
import com.hanserwei.hanmenu.payment.api.PaymentFacts;
import com.hanserwei.hanmenu.reporting.domain.ProjectionState;
import com.hanserwei.hanmenu.reporting.domain.ReportingFacts;
import com.hanserwei.hanmenu.reporting.domain.ReportingRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 在同一数据库一致性快照内原子重建，事件写入等待控制锁，普通读者仍看到旧的已提交投影. */
@Service
@Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 180)
public class ProjectionMaintenance {
  private final ReportingRepository repository;
  private final OrderFacts orders;
  private final CustomerFacts customers;
  private final PaymentFacts payments;
  private final StaffAuthorization authorization;
  private final Clock clock;

  /** 只通过各模块公开快照契约读取源事实，不访问其他模块业务表. */
  public ProjectionMaintenance(
      ReportingRepository repository,
      OrderFacts orders,
      CustomerFacts customers,
      PaymentFacts payments,
      StaffAuthorization authorization,
      Clock clock) {
    this.repository = repository;
    this.orders = orders;
    this.customers = customers;
    this.payments = payments;
    this.authorization = authorization;
    this.clock = clock;
  }

  /** 初次启动自动补齐 P1—P5 已有数据；失败会完整回滚并保持未就绪. */
  public void initialize() {
    var state = repository.lock();
    if (!state.initialized()) {
      rebuild(state);
    }
  }

  /** 管理员按版本重建，失败不能留下部分投影或提前切换代际. */
  public ReportViews.ProjectionStatus rebuild(StaffIdentity actor, long version) {
    authorization.requireAdministrator(actor);
    var state = repository.lock();
    state.requireVersion(version);
    rebuild(state);
    return ReportViews.projection(repository.state(), repository.counts());
  }

  private void rebuild(ProjectionState state) {
    repository.clear();
    copy(
        cursor -> orders.after(cursor, 200),
        OrderFacts.Snapshot::id,
        value -> repository.order(FactMapper.order(value)));
    copy(
        cursor -> customers.after(cursor, 200),
        CustomerFacts.Registration::id,
        value -> repository.customer(new ReportingFacts.Customer(value.id(), value.createdAt())));
    copy(
        cursor -> payments.receiptsAfter(cursor, 200),
        PaymentFacts.Receipt::id,
        value ->
            repository.receipt(
                new ReportingFacts.Receipt(
                    value.id(), value.orderId(), value.amount(), value.paidAt())));
    copy(
        cursor -> payments.refundsAfter(cursor, 200),
        PaymentFacts.Refund::id,
        value ->
            repository.refund(
                new ReportingFacts.Refund(
                    value.id(),
                    value.paymentId(),
                    value.orderId(),
                    value.amount(),
                    value.confirmedAt())));
    state.rebuilt(clock.instant());
    repository.saveState(state);
  }

  private <T> void copy(Function<UUID, List<T>> reader, Function<T, UUID> id, Consumer<T> writer) {
    UUID cursor = null;
    while (true) {
      var batch = reader.apply(cursor);
      for (T value : batch) {
        writer.accept(value);
      }
      repository.finishBatch();
      if (batch.size() < 200) {
        return;
      }
      cursor = id.apply(batch.getLast());
    }
  }
}
