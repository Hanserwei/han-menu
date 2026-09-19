package com.hanserwei.hanmenu.notification.application;

import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.hanserwei.hanmenu.identity.api.StaffSessions;
import com.hanserwei.hanmenu.notification.domain.NotificationException;
import com.hanserwei.hanmenu.notification.domain.NotificationRepository;
import com.hanserwei.hanmenu.notification.domain.StreamTicket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 签发和原子消费三十秒一次性票据，票据绑定原员工数据库会话. */
@Service
@Transactional
public class StreamTicketService {
  private final NotificationRepository repository;
  private final StaffSessions sessions;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  /** 组合消息仓储与身份公开会话校验端口. */
  public StreamTicketService(
      NotificationRepository repository, StaffSessions sessions, Clock clock) {
    this.repository = repository;
    this.sessions = sessions;
    this.clock = clock;
  }

  /** 原始 Bearer 只用于当前认证，不存入票据表或 WebSocket URL. */
  public Ticket issue(String bearer) {
    var proof = sessions.authenticate(bearer).orElseThrow(StreamTicketService::invalid);
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String secret = "hmw_" + BaseEncoding.base64Url().omitPadding().encode(bytes);
    Instant expires = clock.instant().plusSeconds(30);
    repository.issue(
        new StreamTicket(
            proof.sessionHash(),
            digest(secret),
            proof.employeeId(),
            proof.securityVersion(),
            expires,
            null),
        clock.instant());
    return new Ticket(secret, expires, "han-menu.notifications.v1");
  }

  /** 握手必须持票并重验原会话，使用行锁让并发重放只有一次成功. */
  public StaffSessions.Proof consume(String value) {
    if (value == null || !value.matches("hmw_[A-Za-z0-9_-]{43}")) {
      throw invalid();
    }
    var ticket = repository.lockTicket(digest(value)).orElseThrow(StreamTicketService::invalid);
    var proof =
        new StaffSessions.Proof(
            ticket.employeeId(), ticket.securityVersion(), ticket.sessionHash());
    if (!sessions.active(proof)) {
      throw invalid();
    }
    ticket.consume(clock.instant());
    repository.consume(ticket);
    return proof;
  }

  private String digest(String value) {
    return Hashing.sha256().hashString(value, StandardCharsets.UTF_8).toString();
  }

  private static NotificationException invalid() {
    return new NotificationException(
        NotificationException.Reason.INVALID_CREDENTIALS, "员工会话或连接票据已失效");
  }

  /** 一次性票据只在签发响应出现，字符串表示始终脱敏. */
  public record Ticket(String ticket, Instant expiresAt, String protocol) {
    @Override
    public String toString() {
      return "StreamTicket[凭证已隐藏]";
    }
  }
}
