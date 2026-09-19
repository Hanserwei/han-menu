package com.hanserwei.hanmenu.notification.domain;

/** 通知稳定业务分类，不携带凭证或发送报文. */
public final class NotificationException extends RuntimeException {
  private final Reason reason;

  /** 保存可公开的错误原因. */
  public NotificationException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  /** 返回稳定错误分类. */
  public Reason reason() {
    return reason;
  }

  /** 区分输入、版本、状态和认证失败. */
  public enum Reason {
    INVALID_INPUT,
    NOT_FOUND,
    VERSION_CONFLICT,
    CONFLICT,
    INVALID_CREDENTIALS
  }
}
