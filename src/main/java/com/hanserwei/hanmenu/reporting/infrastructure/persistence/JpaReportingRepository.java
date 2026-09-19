package com.hanserwei.hanmenu.reporting.infrastructure.persistence;

import com.hanserwei.hanmenu.reporting.domain.BusinessTime;
import com.hanserwei.hanmenu.reporting.domain.ProjectionState;
import com.hanserwei.hanmenu.reporting.domain.ReportData;
import com.hanserwei.hanmenu.reporting.domain.ReportPeriod;
import com.hanserwei.hanmenu.reporting.domain.ReportingException;
import com.hanserwei.hanmenu.reporting.domain.ReportingFacts;
import com.hanserwei.hanmenu.reporting.domain.ReportingRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 通过本模块 JPA 投影做分组聚合，不执行跨模块 SQL 或逐日逐商品循环查询. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JpaReportingRepository implements ReportingRepository {
  private final ProjectionRecords controls;
  private final OrderFactRecords orders;
  private final LineFactRecords lines;
  private final ProductFactRecords products;
  private final CustomerFactRecords customers;
  private final ReceiptFactRecords receipts;
  private final RefundFactRecords refunds;
  private final EntityManager entityManager;

  JpaReportingRepository(
      ProjectionRecords controls,
      OrderFactRecords orders,
      LineFactRecords lines,
      ProductFactRecords products,
      CustomerFactRecords customers,
      ReceiptFactRecords receipts,
      RefundFactRecords refunds,
      EntityManager entityManager) {
    this.controls = controls;
    this.orders = orders;
    this.lines = lines;
    this.products = products;
    this.customers = customers;
    this.receipts = receipts;
    this.refunds = refunds;
    this.entityManager = entityManager;
  }

  @Override
  public ProjectionState state() {
    return snapshot(controls.findById(1).orElseThrow());
  }

  @Override
  public ProjectionState lock() {
    return snapshot(controls.findLockedById(1).orElseThrow());
  }

  private ProjectionState snapshot(ProjectionEntity value) {
    return new ProjectionState(
        value.version,
        value.generation,
        value.revision,
        value.initialized,
        value.updatedAt,
        value.rebuiltAt);
  }

  @Override
  public void saveState(ProjectionState state) {
    var entity = controls.findById(1).orElseThrow();
    if (entity.version != state.version()) {
      throw new ObjectOptimisticLockingFailureException(ProjectionEntity.class, 1);
    }
    entity.generation = state.generation();
    entity.revision = state.revision();
    entity.initialized = state.initialized();
    entity.updatedAt = state.updatedAt();
    entity.rebuiltAt = state.rebuiltAt();
    controls.flush();
  }

  @Override
  public boolean order(ReportingFacts.Order value) {
    var existing = orders.findById(value.id());
    if (existing.isPresent()) {
      var entity = existing.orElseThrow();
      if (!value.newerThan(entity.sourceVersion)) {
        return false;
      }
      if (entity.total.compareTo(value.total()) != 0
          || !entity.customerId.equals(value.customerId())
          || !entity.createdAt.equals(value.createdAt())) {
        conflict();
      }
      entity.apply(value);
    } else {
      for (var line : value.lines()) {
        var product = products.findById(line.productId()).orElseGet(ProductFactEntity::new);
        if (product.id == null
            || value.createdAt().isAfter(product.observedAt)
            || (value.createdAt().equals(product.observedAt)
                && value.id().toString().compareTo(product.sourceOrderId.toString()) > 0)) {
          product.id = line.productId();
          product.name = line.name();
          product.kind = line.kind();
          product.observedAt = value.createdAt();
          product.sourceOrderId = value.id();
          products.save(product);
        }
      }
      orders.save(OrderFactEntity.from(value));
    }
    return true;
  }

  @Override
  public boolean customer(ReportingFacts.Customer value) {
    var existing = customers.findById(value.id());
    if (existing.isPresent()) {
      if (!existing.orElseThrow().createdAt.equals(value.createdAt())) {
        conflict();
      }
      return false;
    }
    var entity = new CustomerFactEntity();
    entity.id = value.id();
    entity.createdAt = value.createdAt();
    entity.createdDate = BusinessTime.date(value.createdAt());
    customers.save(entity);
    return true;
  }

  @Override
  public boolean receipt(ReportingFacts.Receipt value) {
    var existing = receipts.findById(value.id());
    if (existing.isPresent()) {
      var entity = existing.orElseThrow();
      if (!new ReportingFacts.Receipt(entity.id, entity.orderId, entity.amount, entity.paidAt)
          .equals(value)) {
        conflict();
      }
      return false;
    }
    var entity = new ReceiptFactEntity();
    entity.id = value.id();
    entity.orderId = value.orderId();
    entity.amount = value.amount();
    entity.paidAt = value.paidAt();
    entity.businessDate = BusinessTime.date(value.paidAt());
    receipts.save(entity);
    return true;
  }

  @Override
  public boolean refund(ReportingFacts.Refund value) {
    var existing = refunds.findById(value.id());
    if (existing.isPresent()) {
      var entity = existing.orElseThrow();
      if (!new ReportingFacts.Refund(
              entity.id, entity.paymentId, entity.orderId, entity.amount, entity.confirmedAt)
          .equals(value)) {
        conflict();
      }
      return false;
    }
    var entity = new RefundFactEntity();
    entity.id = value.id();
    entity.paymentId = value.paymentId();
    entity.orderId = value.orderId();
    entity.amount = value.amount();
    entity.confirmedAt = value.confirmedAt();
    entity.businessDate = BusinessTime.date(value.confirmedAt());
    refunds.save(entity);
    return true;
  }

  private void conflict() {
    throw new ReportingException(ReportingException.Reason.CONFLICT, "不可变统计事实发生冲突");
  }

  @Override
  public void clear() {
    lines.deleteAllInBatch();
    orders.deleteAllInBatch();
    products.deleteAllInBatch();
    customers.deleteAllInBatch();
    receipts.deleteAllInBatch();
    refunds.deleteAllInBatch();
  }

  @Override
  public void finishBatch() {
    entityManager.flush();
    entityManager.clear();
  }

  @Override
  public Counts counts() {
    return new Counts(orders.count(), customers.count(), receipts.count(), refunds.count());
  }

  @Override
  public ReportData.Aggregates aggregate(ReportPeriod period) {
    return new ReportData.Aggregates(
        orderDays(period),
        completionDays(period),
        cashDays(ReceiptFactEntity.class, period),
        cashDays(RefundFactEntity.class, period),
        customerDays(period),
        customers.countByCreatedDateBefore(period.from()));
  }

  private List<ReportData.OrderDay> orderDays(ReportPeriod period) {
    var cb = entityManager.getCriteriaBuilder();
    var query = cb.createTupleQuery();
    var root = query.from(OrderFactEntity.class);
    var date = root.<LocalDate>get("createdDate");
    query
        .multiselect(
            date,
            cb.count(root),
            cb.sum(
                cb.<Long>selectCase()
                    .when(cb.equal(root.get("status"), "COMPLETED"), 1L)
                    .otherwise(0L)),
            cb.sum(
                cb.<Long>selectCase()
                    .when(cb.equal(root.get("status"), "CANCELLED"), 1L)
                    .otherwise(0L)))
        .where(
            ReportingSpecifications.<OrderFactEntity>dates("createdDate", period)
                .toPredicate(root, query, cb))
        .groupBy(date)
        .orderBy(cb.asc(date));
    return entityManager.createQuery(query).getResultList().stream()
        .map(
            row ->
                new ReportData.OrderDay(
                    row.get(0, LocalDate.class), number(row, 1), number(row, 2), number(row, 3)))
        .toList();
  }

  private List<ReportData.CompletionDay> completionDays(ReportPeriod period) {
    var cb = entityManager.getCriteriaBuilder();
    var query = cb.createTupleQuery();
    var root = query.from(OrderFactEntity.class);
    var date = root.<LocalDate>get("completedDate");
    query
        .multiselect(date, cb.count(root), cb.sum(root.<BigDecimal>get("total")))
        .where(ReportingSpecifications.completed(period).toPredicate(root, query, cb))
        .groupBy(date)
        .orderBy(cb.asc(date));
    return entityManager.createQuery(query).getResultList().stream()
        .map(
            row ->
                new ReportData.CompletionDay(
                    row.get(0, LocalDate.class), number(row, 1), row.get(2, BigDecimal.class)))
        .toList();
  }

  private <T> List<ReportData.CashDay> cashDays(Class<T> type, ReportPeriod period) {
    var cb = entityManager.getCriteriaBuilder();
    var query = cb.createTupleQuery();
    var root = query.from(type);
    var date = root.<LocalDate>get("businessDate");
    query
        .multiselect(date, cb.count(root), cb.sum(root.<BigDecimal>get("amount")))
        .where(
            ReportingSpecifications.<T>dates("businessDate", period).toPredicate(root, query, cb))
        .groupBy(date)
        .orderBy(cb.asc(date));
    return entityManager.createQuery(query).getResultList().stream()
        .map(
            row ->
                new ReportData.CashDay(
                    row.get(0, LocalDate.class), number(row, 1), row.get(2, BigDecimal.class)))
        .toList();
  }

  private List<ReportData.CustomerDay> customerDays(ReportPeriod period) {
    var cb = entityManager.getCriteriaBuilder();
    var query = cb.createTupleQuery();
    var root = query.from(CustomerFactEntity.class);
    var date = root.<LocalDate>get("createdDate");
    query
        .multiselect(date, cb.count(root))
        .where(
            ReportingSpecifications.<CustomerFactEntity>dates("createdDate", period)
                .toPredicate(root, query, cb))
        .groupBy(date)
        .orderBy(cb.asc(date));
    return entityManager.createQuery(query).getResultList().stream()
        .map(row -> new ReportData.CustomerDay(row.get(0, LocalDate.class), number(row, 1)))
        .toList();
  }

  @Override
  public List<ReportData.Sale> sales(ReportPeriod period, int limit) {
    var cb = entityManager.getCriteriaBuilder();
    var query = cb.createTupleQuery();
    var root = query.from(LineFactEntity.class);
    var product = root.join("product");
    var quantity = cb.sumAsLong(root.<Integer>get("quantity"));
    var amount = cb.sum(root.<BigDecimal>get("subtotal"));
    query
        .multiselect(root.get("productId"), root.get("kind"), product.get("name"), quantity, amount)
        .where(ReportingSpecifications.completedSales(period).toPredicate(root, query, cb))
        .groupBy(root.get("productId"), root.get("kind"), product.get("name"))
        .orderBy(cb.desc(quantity), cb.desc(amount), cb.asc(root.get("productId")));
    return entityManager.createQuery(query).setMaxResults(limit).getResultList().stream()
        .map(
            row ->
                new ReportData.Sale(
                    row.get(0, UUID.class),
                    row.get(1, String.class),
                    row.get(2, String.class),
                    number(row, 3),
                    row.get(4, BigDecimal.class)))
        .toList();
  }

  @Override
  public ReportData.Reconciliation reconcile(ReportPeriod period) {
    return new ReportData.Reconciliation(
        missingOrders(period),
        paymentMismatches(period),
        missingPayments(period),
        refundMismatches(period),
        orders.countByRefundStatus("PENDING"),
        ordersMissingReceipts(),
        ordersMissingRefunds());
  }

  private long ordersMissingReceipts() {
    var cb = entityManager.getCriteriaBuilder();
    var query = cb.createQuery(Long.class);
    var order = query.from(OrderFactEntity.class);
    var source = query.subquery(UUID.class);
    var receipt = source.from(ReceiptFactEntity.class);
    source
        .select(receipt.get("id"))
        .where(
            cb.equal(receipt.get("id"), order.get("paymentId")),
            cb.equal(receipt.get("orderId"), order.get("id")));
    query
        .select(cb.count(order))
        .where(cb.isNotNull(order.get("paidAt")), cb.not(cb.exists(source)));
    return entityManager.createQuery(query).getSingleResult();
  }

  private long ordersMissingRefunds() {
    var cb = entityManager.getCriteriaBuilder();
    var query = cb.createQuery(Long.class);
    var order = query.from(OrderFactEntity.class);
    var source = query.subquery(UUID.class);
    var refund = source.from(RefundFactEntity.class);
    source
        .select(refund.get("id"))
        .where(
            cb.equal(refund.get("id"), order.get("refundId")),
            cb.equal(refund.get("orderId"), order.get("id")),
            cb.equal(refund.get("paymentId"), order.get("paymentId")));
    query
        .select(cb.count(order))
        .where(cb.equal(order.get("refundStatus"), "SUCCEEDED"), cb.not(cb.exists(source)));
    return entityManager.createQuery(query).getSingleResult();
  }

  private long missingOrders(ReportPeriod period) {
    var cb = entityManager.getCriteriaBuilder();
    var query = cb.createQuery(Long.class);
    var receipt = query.from(ReceiptFactEntity.class);
    var source = query.subquery(UUID.class);
    var order = source.from(OrderFactEntity.class);
    source.select(order.get("id")).where(cb.equal(order.get("id"), receipt.get("orderId")));
    query
        .select(cb.count(receipt))
        .where(
            ReportingSpecifications.<ReceiptFactEntity>dates("businessDate", period)
                .toPredicate(receipt, query, cb),
            cb.not(cb.exists(source)));
    return entityManager.createQuery(query).getSingleResult();
  }

  private long paymentMismatches(ReportPeriod period) {
    var cb = entityManager.getCriteriaBuilder();
    var query = cb.createQuery(Long.class);
    var receipt = query.from(ReceiptFactEntity.class);
    var source = query.subquery(UUID.class);
    var order = source.from(OrderFactEntity.class);
    source
        .select(order.get("id"))
        .where(
            cb.equal(order.get("id"), receipt.get("orderId")),
            cb.or(
                cb.isNull(order.get("paymentId")),
                cb.notEqual(order.get("paymentId"), receipt.get("id")),
                cb.notEqual(order.get("total"), receipt.get("amount"))));
    query
        .select(cb.count(receipt))
        .where(
            ReportingSpecifications.<ReceiptFactEntity>dates("businessDate", period)
                .toPredicate(receipt, query, cb),
            cb.exists(source));
    return entityManager.createQuery(query).getSingleResult();
  }

  private long missingPayments(ReportPeriod period) {
    var cb = entityManager.getCriteriaBuilder();
    var query = cb.createQuery(Long.class);
    var refund = query.from(RefundFactEntity.class);
    var source = query.subquery(UUID.class);
    var receipt = source.from(ReceiptFactEntity.class);
    source.select(receipt.get("id")).where(cb.equal(receipt.get("id"), refund.get("paymentId")));
    query
        .select(cb.count(refund))
        .where(
            ReportingSpecifications.<RefundFactEntity>dates("businessDate", period)
                .toPredicate(refund, query, cb),
            cb.not(cb.exists(source)));
    return entityManager.createQuery(query).getSingleResult();
  }

  private long refundMismatches(ReportPeriod period) {
    var cb = entityManager.getCriteriaBuilder();
    var query = cb.createQuery(Long.class);
    var refund = query.from(RefundFactEntity.class);
    var source = query.subquery(UUID.class);
    var receipt = source.from(ReceiptFactEntity.class);
    source
        .select(receipt.get("id"))
        .where(
            cb.equal(receipt.get("id"), refund.get("paymentId")),
            cb.or(
                cb.notEqual(receipt.get("orderId"), refund.get("orderId")),
                cb.notEqual(receipt.get("amount"), refund.get("amount"))));
    query
        .select(cb.count(refund))
        .where(
            ReportingSpecifications.<RefundFactEntity>dates("businessDate", period)
                .toPredicate(refund, query, cb),
            cb.exists(source));
    return entityManager.createQuery(query).getSingleResult();
  }

  @Override
  public Map<String, Long> statuses() {
    var cb = entityManager.getCriteriaBuilder();
    var query = cb.createTupleQuery();
    var root = query.from(OrderFactEntity.class);
    query.multiselect(root.get("status"), cb.count(root)).groupBy(root.get("status"));
    return entityManager.createQuery(query).getResultList().stream()
        .collect(
            java.util.stream.Collectors.toMap(
                row -> row.get(0, String.class), row -> number(row, 1)));
  }

  @Override
  public Map<String, Long> today(LocalDate date) {
    return Map.of(
        "created",
        orders.countByCreatedDate(date),
        "completed",
        orders.countByStatusAndCompletedDate("COMPLETED", date));
  }

  private long number(Tuple row, int index) {
    return ((Number) row.get(index)).longValue();
  }
}
