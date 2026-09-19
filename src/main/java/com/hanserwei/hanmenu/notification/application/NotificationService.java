package com.hanserwei.hanmenu.notification.application;

import com.hanserwei.hanmenu.identity.api.StaffAuthorization;
import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import com.hanserwei.hanmenu.notification.domain.Notice;
import com.hanserwei.hanmenu.notification.domain.NotificationException;
import com.hanserwei.hanmenu.notification.domain.NotificationPush;
import com.hanserwei.hanmenu.notification.domain.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 通知短事务负责去重、领取、结果登记和个人阅读进度，不写网络连接. */
@Service
@Transactional
public class NotificationService {
  private final NotificationRepository repository;
  private final StaffAuthorization authorization;
  private final Clock clock;

  /** 组合本模块仓储及员工公开授权能力. */
  public NotificationService(
      NotificationRepository repository, StaffAuthorization authorization, Clock clock) {
    this.repository = repository;
    this.authorization = authorization;
    this.clock = clock;
  }

  /** 事件消费者在同一事务追加消息，唯一事件标识避免重复通知. */
  public void append(UUID id, UUID orderId, Notice.Kind kind, Instant occurredAt) {
    repository.append(id, orderId, kind, occurredAt, clock.instant());
  }

  /** 补查仅前移到本次真实返回的最后游标，不能跳过分页间并发提交的新消息. */
  @Transactional(readOnly = true)
  public Feed feed(StaffIdentity actor, long after, int limit) {
    authorization.requireStaff(actor);
    if (after < 0 || limit < 1 || limit > 100 || after > repository.head()) {
      throw new NotificationException(NotificationException.Reason.INVALID_INPUT, "通知游标或分页范围不合法");
    }
    var all = repository.after(after, limit + 1);
    var items = all.stream().limit(limit).map(NotificationService::view).toList();
    return new Feed(
        items, items.isEmpty() ? after : items.getLast().sequence(), all.size() > limit);
  }

  /** 阅读进度按当前员工隔离. */
  @Transactional(readOnly = true)
  public ReceiptView receipt(StaffIdentity actor) {
    authorization.requireStaff(actor);
    var value = repository.receipt(actor.employeeId());
    return new ReceiptView(value.sequence(), value.version(), value.updatedAt());
  }

  /** 仅允许按版本前移本人的阅读游标，不能确认尚未提交的消息. */
  public ReceiptView acknowledge(StaffIdentity actor, long sequence, long version) {
    authorization.requireStaff(actor);
    var current = repository.receipt(actor.employeeId());
    var next = current.acknowledge(sequence, version, repository.head(), clock.instant());
    if (next.sequence() != current.sequence()) {
      repository.saveReceipt(next);
    }
    return receipt(actor);
  }

  /** 管理员查看有界发送轨迹，普通员工不读取运维失败信息. */
  @Transactional(readOnly = true)
  public List<AttemptView> attempts(StaffIdentity actor, UUID id) {
    authorization.requireAdministrator(actor);
    repository
        .find(id)
        .orElseThrow(
            () -> new NotificationException(NotificationException.Reason.NOT_FOUND, "通知不存在"));
    return repository.attempts(id, 100).stream()
        .map(
            value ->
                new AttemptView(
                    value.id(),
                    value.startedAt(),
                    value.finishedAt(),
                    value.status(),
                    value.sent(),
                    value.failed(),
                    value.failure()))
        .toList();
  }

  /** 按当前通知版本重启已耗尽的实时投递，持久化历史无需重建. */
  public NoticeView redeliver(StaffIdentity actor, UUID id, long version) {
    authorization.requireAdministrator(actor);
    var notice = repository.lock(id);
    notice.retry(version, clock.instant());
    repository.update(notice);
    return view(repository.find(id).orElseThrow());
  }

  /** 返回有界待处理消息. */
  @Transactional(readOnly = true)
  public List<UUID> due() {
    return repository.due(clock.instant(), 100);
  }

  /** 领取和尝试记录在同一短事务中提交. */
  public Optional<Notice> claim(UUID id) {
    var notice = repository.lock(id);
    var previous = notice.activeAttemptId();
    if (notice.claim(clock.instant()) == null) {
      return Optional.empty();
    }
    repository.startAttempt(notice, previous, clock.instant());
    repository.update(notice);
    return Optional.of(notice);
  }

  /** 旧领取结果只登记失效，不覆盖新投递进度. */
  public void finish(UUID id, UUID attemptId, NotificationPush.Outcome outcome) {
    var notice = repository.lock(id);
    String failure = outcome.successful() ? null : outcome.failure();
    boolean current = notice.finish(attemptId, outcome.successful(), failure, clock.instant());
    if (current) {
      repository.update(notice);
    }
    repository.finishAttempt(
        attemptId, current, outcome.sent(), outcome.failed(), failure, clock.instant());
  }

  private static NoticeView view(Notice notice) {
    return new NoticeView(
        notice.id(),
        notice.sequence(),
        notice.orderId(),
        notice.kind().name(),
        notice.occurredAt(),
        notice.status().name(),
        notice.attempts(),
        notice.lastFailure(),
        notice.version());
  }

  /** 公共通知查询 DTO 不含收货资料. */
  public record NoticeView(
      UUID id,
      long sequence,
      UUID orderId,
      String type,
      Instant occurredAt,
      String deliveryStatus,
      int attempts,
      String lastFailure,
      long version) {}

  /** 分页重连结果；客户端按 sequence 去重并持续补查直到 hasMore=false. */
  public record Feed(List<NoticeView> items, long nextCursor, boolean hasMore) {
    /** 固定消息列表. */
    public Feed {
      items = List.copyOf(items);
    }
  }

  /** 个人阅读确认与服务器实时投递状态分别存储. */
  public record ReceiptView(long sequence, long version, Instant updatedAt) {}

  /** 有界投递尝试，不含网络错误原文. */
  public record AttemptView(
      UUID id,
      Instant startedAt,
      Instant finishedAt,
      String status,
      int sent,
      int failed,
      String failure) {}
}
