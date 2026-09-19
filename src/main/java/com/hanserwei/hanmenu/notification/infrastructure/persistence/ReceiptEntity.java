package com.hanserwei.hanmenu.notification.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Receipt 的通知模块内部持久化模型. */
@Entity(name = "NotificationReceipt")
@Table(name = "notification_receipt")
public class ReceiptEntity {
  @Id UUID employeeId;
  long sequence;
  Instant updatedAt;
  @jakarta.persistence.Version Long version;

  /** ORM 重建入口. */
  protected ReceiptEntity() {}
}
