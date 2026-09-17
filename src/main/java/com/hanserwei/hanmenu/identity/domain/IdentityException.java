package com.hanserwei.hanmenu.identity.domain;

/** 身份用例的可预期失败，消息可安全返回客户端，不携带原始请求或凭证. */
public final class IdentityException extends RuntimeException {
  private final Reason reason;

  /** 创建具有明确业务分类的失败，不接收底层 SQL 或认证头作为消息. */
  public IdentityException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  /** 返回失败分类，由接口层映射为对应的 HTTP 状态. */
  public Reason reason() {
    return reason;
  }

  /** 区分认证、权限、数据冲突、限流及基础设施不可用等边界. */
  public enum Reason {
    INVALID_CREDENTIALS,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    VERSION_CONFLICT,
    INVALID_INPUT,
    RATE_LIMITED,
    UNAVAILABLE
  }
}
