package com.hanserwei.hanmenu.reporting.domain;

/** 报表输入、投影状态和并发冲突的稳定错误分类. */
public final class ReportingException extends RuntimeException {
  private final Reason reason;

  /** 保存不包含个人资料的业务错误. */
  public ReportingException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  /** 返回错误分类. */
  public Reason reason() {
    return reason;
  }

  /** 未初始化的投影不能被误读为零营业数据. */
  public enum Reason {
    INVALID_INPUT,
    VERSION_CONFLICT,
    CONFLICT,
    NOT_READY,
    UNAVAILABLE
  }
}
