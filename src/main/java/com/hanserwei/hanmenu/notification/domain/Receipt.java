package com.hanserwei.hanmenu.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 每位员工独立的阅读进度聚合，版本和单调性防止多设备覆盖新进度. */
public final class Receipt {
  private final UUID employeeId;
  private final long sequence;
  private final long version;
  private final Instant updatedAt;

  /** 重建有身份的阅读进度，缺省进度由应用以零游标表达. */
  public Receipt(UUID employeeId, long sequence, long version, Instant updatedAt) {
    if (sequence < 0 || version < 0) {
      throw new IllegalArgumentException("阅读游标不合法");
    }
    this.employeeId = Objects.requireNonNull(employeeId);
    this.sequence = sequence;
    this.version = version;
    this.updatedAt = Objects.requireNonNull(updatedAt);
  }

  /** 返回前移后的同一员工阅读进度，不能越过当前已提交消息头或倒退. */
  public Receipt acknowledge(long target, long expectedVersion, long head, Instant now) {
    if (version != expectedVersion) {
      throw new NotificationException(NotificationException.Reason.VERSION_CONFLICT, "阅读进度已更新");
    }
    if (target < sequence || target > head) {
      throw new NotificationException(
          NotificationException.Reason.INVALID_INPUT, "阅读游标不能倒退或超出消息范围");
    }
    return target == sequence ? this : new Receipt(employeeId, target, version, now);
  }

  /** 返回阅读进度所属员工. */
  public UUID employeeId() {
    return employeeId;
  }

  /** 返回已经确认的最高提交游标. */
  public long sequence() {
    return sequence;
  }

  /** 返回持久化版本. */
  public long version() {
    return version;
  }

  /** 返回最后确认时间. */
  public Instant updatedAt() {
    return updatedAt;
  }
}
