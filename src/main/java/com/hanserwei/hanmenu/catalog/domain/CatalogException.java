package com.hanserwei.hanmenu.catalog.domain;

/** 目录业务失败，仅携带可安全返回的分类和说明. */
public final class CatalogException extends RuntimeException {
  private final Reason reason;

  /** 构造业务失败，禁止将底层存储异常内容作为提示. */
  public CatalogException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  /** 返回用于 HTTP 映射的业务分类. */
  public Reason reason() {
    return reason;
  }

  /** 参数、缺失、业务状态、并发及外部服务失败的分类. */
  public enum Reason {
    INVALID_INPUT,
    NOT_FOUND,
    CONFLICT,
    VERSION_CONFLICT,
    UNAVAILABLE
  }
}
