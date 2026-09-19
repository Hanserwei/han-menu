package com.hanserwei.hanmenu.notification.application;

import com.hanserwei.hanmenu.notification.domain.NotificationPush;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 事务外发送已持久化通知，任何连接故障都保留可补查历史及可重试记录. */
@Service
@Transactional(propagation = Propagation.NEVER)
public class NotificationDispatcher {
  private final NotificationService notifications;
  private final NotificationPush push;

  /** 分离短事务和网络发送. */
  public NotificationDispatcher(NotificationService notifications, NotificationPush push) {
    this.notifications = notifications;
    this.push = push;
  }

  /** 有界领取并逐项发送，每次使用独立的持久化尝试标识. */
  public void dispatch() {
    for (UUID id : notifications.due()) {
      deliver(id);
    }
  }

  /** 发送单条通知，发送失败也以固定分类结束尝试. */
  public void deliver(UUID id) {
    var claimed = notifications.claim(id);
    if (claimed.isEmpty()) {
      return;
    }
    var notice = claimed.orElseThrow();
    NotificationPush.Outcome outcome;
    try {
      outcome = push.send(notice);
    } catch (RuntimeException exception) {
      outcome = new NotificationPush.Outcome(0, 1);
    }
    notifications.finish(id, notice.activeAttemptId(), outcome);
  }

  /** 空闲连接也定期验证，不允许退出后的连接无限存活. */
  public void prune() {
    push.prune();
  }
}
