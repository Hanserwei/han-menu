package com.hanserwei.hanmenu.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 一次性长连接票据只保存摘要，并绑定原始员工会话及安全版本. */
public final class StreamTicket {
  private final String sessionHash;
  private final String ticketHash;
  private final UUID employeeId;
  private final long securityVersion;
  private final Instant expiresAt;
  private Instant consumedAt;

  /** 重建或签发票据快照，摘要不允许空值. */
  public StreamTicket(
      String sessionHash,
      String ticketHash,
      UUID employeeId,
      long securityVersion,
      Instant expiresAt,
      Instant consumedAt) {
    this.sessionHash = Objects.requireNonNull(sessionHash);
    this.ticketHash = Objects.requireNonNull(ticketHash);
    this.employeeId = Objects.requireNonNull(employeeId);
    this.securityVersion = securityVersion;
    this.expiresAt = Objects.requireNonNull(expiresAt);
    this.consumedAt = consumedAt;
  }

  /** 仅在到期前消费一次，重放或过期都不能建立连接. */
  public void consume(Instant now) {
    if (consumedAt != null || !now.isBefore(expiresAt)) {
      throw new NotificationException(NotificationException.Reason.INVALID_CREDENTIALS, "连接票据已失效");
    }
    consumedAt = now;
  }

  /** 返回原会话摘要供服务端复验. */
  public String sessionHash() {
    return sessionHash;
  }

  /** 返回票据摘要. */
  public String ticketHash() {
    return ticketHash;
  }

  /** 返回员工标识. */
  public UUID employeeId() {
    return employeeId;
  }

  /** 返回签发时账号安全版本. */
  public long securityVersion() {
    return securityVersion;
  }

  /** 返回到期时刻. */
  public Instant expiresAt() {
    return expiresAt;
  }

  /** 返回消费时刻，未使用时为空. */
  public Instant consumedAt() {
    return consumedAt;
  }

  @Override
  public String toString() {
    return "StreamTicket[凭证已隐藏]";
  }
}
