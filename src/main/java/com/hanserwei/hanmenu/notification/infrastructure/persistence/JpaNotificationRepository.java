package com.hanserwei.hanmenu.notification.infrastructure.persistence;

import com.hanserwei.hanmenu.notification.domain.Notice;
import com.hanserwei.hanmenu.notification.domain.NotificationException;
import com.hanserwei.hanmenu.notification.domain.NotificationRepository;
import com.hanserwei.hanmenu.notification.domain.Receipt;
import com.hanserwei.hanmenu.notification.domain.StreamTicket;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 通知数据均属于本模块，提交顺序行锁避免游标跨过尚未提交的消息. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JpaNotificationRepository implements NotificationRepository {
  private final NoticeRecords notices;
  private final FeedRecords feeds;
  private final AttemptRecords attempts;
  private final ReceiptRecords receipts;
  private final TicketRecords tickets;

  JpaNotificationRepository(
      NoticeRecords notices,
      FeedRecords feeds,
      AttemptRecords attempts,
      ReceiptRecords receipts,
      TicketRecords tickets) {
    this.notices = notices;
    this.feeds = feeds;
    this.attempts = attempts;
    this.receipts = receipts;
    this.tickets = tickets;
  }

  @Override
  public void append(UUID id, UUID orderId, Notice.Kind kind, Instant occurredAt, Instant now) {
    var feed = feeds.findLockedById(1).orElseThrow();
    if (notices.existsById(id)) {
      return;
    }
    feed.sequence = Math.incrementExact(feed.sequence);
    notices.saveAndFlush(
        NoticeEntity.from(Notice.create(id, feed.sequence, orderId, kind, occurredAt, now)));
  }

  @Override
  public List<Notice> after(long sequence, int limit) {
    return notices
        .findBySequenceGreaterThanOrderBySequenceAsc(sequence, PageRequest.of(0, limit))
        .stream()
        .map(NoticeEntity::domain)
        .toList();
  }

  @Override
  public long head() {
    return feeds.findById(1).orElseThrow().sequence;
  }

  @Override
  public Optional<Notice> find(UUID id) {
    return notices.findById(id).map(NoticeEntity::domain);
  }

  @Override
  public Notice lock(UUID id) {
    return notices
        .findLockedById(id)
        .orElseThrow(
            () -> new NotificationException(NotificationException.Reason.NOT_FOUND, "通知不存在"))
        .domain();
  }

  @Override
  public void update(Notice notice) {
    var entity = notices.findById(notice.id()).orElseThrow();
    if (entity.version != notice.version()) {
      throw new ObjectOptimisticLockingFailureException(NoticeEntity.class, notice.id());
    }
    entity.apply(notice);
    notices.flush();
  }

  @Override
  public List<UUID> due(Instant now, int limit) {
    return notices
        .findByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscSequenceAsc(
            now, PageRequest.of(0, limit))
        .stream()
        .map(value -> value.id)
        .toList();
  }

  @Override
  public void startAttempt(Notice notice, UUID previousAttempt, Instant now) {
    if (previousAttempt != null) {
      attempts
          .findById(previousAttempt)
          .ifPresent(
              value -> {
                if (value.finishedAt == null) {
                  value.status = "EXPIRED";
                  value.finishedAt = now;
                  value.failure = "LEASE_EXPIRED";
                }
              });
    }
    var entity = new AttemptEntity();
    entity.id = notice.activeAttemptId();
    entity.noticeId = notice.id();
    entity.startedAt = now;
    entity.status = "STARTED";
    attempts.saveAndFlush(entity);
  }

  @Override
  public void finishAttempt(
      UUID id, boolean current, int sent, int failed, String reason, Instant now) {
    var entity = attempts.findById(id).orElseThrow();
    if (entity.finishedAt != null) {
      return;
    }
    entity.finishedAt = now;
    entity.sent = sent;
    entity.failed = failed;
    entity.status = !current ? "SUPERSEDED" : reason == null ? "SUCCEEDED" : "FAILED";
    entity.failure = reason;
    attempts.flush();
  }

  @Override
  public List<Attempt> attempts(UUID id, int limit) {
    return attempts.findByNoticeIdOrderByStartedAtDescIdDesc(id, PageRequest.of(0, limit)).stream()
        .map(
            value ->
                new Attempt(
                    value.id,
                    value.startedAt,
                    value.finishedAt,
                    value.status,
                    value.sent,
                    value.failed,
                    value.failure))
        .toList();
  }

  @Override
  public Receipt receipt(UUID employeeId) {
    return receipts
        .findById(employeeId)
        .map(value -> new Receipt(value.employeeId, value.sequence, value.version, value.updatedAt))
        .orElse(new Receipt(employeeId, 0, 0, Instant.EPOCH));
  }

  @Override
  public void saveReceipt(Receipt value) {
    var existing = receipts.findById(value.employeeId());
    var entity =
        existing.orElseGet(
            () -> {
              var created = new ReceiptEntity();
              created.employeeId = value.employeeId();
              created.sequence = 0;
              created.updatedAt = Instant.EPOCH;
              return receipts.saveAndFlush(created);
            });
    if (entity.version != value.version()) {
      throw new ObjectOptimisticLockingFailureException(ReceiptEntity.class, value.employeeId());
    }
    entity.sequence = value.sequence();
    entity.updatedAt = value.updatedAt();
    receipts.flush();
  }

  @Override
  public void issue(StreamTicket ticket, Instant now) {
    tickets.deleteByExpiresAtLessThanEqual(now);
    var entity = tickets.findById(ticket.sessionHash()).orElseGet(TicketEntity::new);
    entity.sessionHash = ticket.sessionHash();
    entity.ticketHash = ticket.ticketHash();
    entity.employeeId = ticket.employeeId();
    entity.securityVersion = ticket.securityVersion();
    entity.expiresAt = ticket.expiresAt();
    entity.consumedAt = null;
    tickets.saveAndFlush(entity);
  }

  @Override
  public Optional<StreamTicket> lockTicket(String hash) {
    return tickets
        .findLockedByTicketHash(hash)
        .map(
            value ->
                new StreamTicket(
                    value.sessionHash,
                    value.ticketHash,
                    value.employeeId,
                    value.securityVersion,
                    value.expiresAt,
                    value.consumedAt));
  }

  @Override
  public void consume(StreamTicket ticket) {
    var entity = tickets.findById(ticket.sessionHash()).orElseThrow();
    entity.consumedAt = ticket.consumedAt();
    tickets.flush();
  }
}
