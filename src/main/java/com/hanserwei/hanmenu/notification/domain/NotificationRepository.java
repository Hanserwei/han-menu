package com.hanserwei.hanmenu.notification.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 持久化消息、领取记录、阅读进度和一次性票据的领域端口. */
public interface NotificationRepository {
  /** 在提交顺序锁内去重业务事件并追加消息，只有提交后才能向客户端补查. */
  void append(UUID id, UUID orderId, Notice.Kind kind, Instant occurredAt, Instant now);

  /** 按提交游标有界读取，排序不依赖 UUID 或可乱序提交的数据库序列. */
  List<Notice> after(long sequence, int limit);

  /** 返回最新已提交游标. */
  long head();

  /** 按标识查询消息. */
  Optional<Notice> find(UUID id);

  /** 锁定消息，串行化领取、重试和结果确认. */
  Notice lock(UUID id);

  /** 保存聚合状态并校验版本. */
  void update(Notice notice);

  /** 读取有限待投递标识. */
  List<UUID> due(Instant now, int limit);

  /** 登记一次领取并将此前未结束的领取标为超时. */
  void startAttempt(Notice notice, UUID previousAttempt, Instant now);

  /** 记录发送结果，不能通过旧结果覆盖新的消息领取. */
  void finishAttempt(UUID id, boolean current, int sent, int failed, String reason, Instant now);

  /** 返回有界投递跟踪记录. */
  List<Attempt> attempts(UUID noticeId, int limit);

  /** 读取个人阅读进度，缺省为零. */
  Receipt receipt(UUID employeeId);

  /** 创建或按版本前移个人阅读进度. */
  void saveReceipt(Receipt receipt);

  /** 每个员工会话只保留最新票据，同时清理过期票据. */
  void issue(StreamTicket ticket, Instant now);

  /** 在行锁内读取票据，保证只有一次握手能够消费. */
  Optional<StreamTicket> lockTicket(String hash);

  /** 保存已消费状态. */
  void consume(StreamTicket ticket);

  /** 投递尝试只记录内部编号、结果和固定失败分类. */
  record Attempt(
      UUID id,
      Instant startedAt,
      Instant finishedAt,
      String status,
      int sent,
      int failed,
      String failure) {}
}
