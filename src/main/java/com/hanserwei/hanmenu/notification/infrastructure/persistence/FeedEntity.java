package com.hanserwei.hanmenu.notification.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Feed 的通知模块内部持久化模型. */
@Entity(name = "NotificationFeed")
@Table(name = "notification_feed")
public class FeedEntity {
  @Id Integer id;
  long sequence;

  /** ORM 重建入口. */
  protected FeedEntity() {}
}
