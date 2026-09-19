package com.hanserwei.hanmenu.notification.web;

import com.hanserwei.hanmenu.identity.api.StaffSessions;
import com.hanserwei.hanmenu.notification.domain.Notice;
import com.hanserwei.hanmenu.notification.domain.NotificationPush;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.json.JsonMapper;

/** 有界员工长连接适配器，实时消息仅作提示，个人阅读确认仍通过版本化 HTTP 资源完成. */
@Component
public class NotificationSocket extends TextWebSocketHandler
    implements NotificationPush, SubProtocolCapable {
  static final String PROTOCOL = "han-menu.notifications.v1";
  static final String PROOF = "verifiedStaffSession";
  private final StaffSessions sessions;
  private final JsonMapper json;
  private final ConcurrentHashMap<String, Connection> connections = new ConcurrentHashMap<>();

  /** 只依赖身份公开契约，不能缓存一次握手永久放行. */
  public NotificationSocket(StaffSessions sessions, JsonMapper json) {
    this.sessions = sessions;
    this.json = json;
  }

  @Override
  public List<String> getSubProtocols() {
    return List.of(PROTOCOL);
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    var proof = (StaffSessions.Proof) session.getAttributes().get(PROOF);
    if (proof == null || !sessions.active(proof)) {
      session.close(CloseStatus.POLICY_VIOLATION);
      return;
    }
    var guarded = new ConcurrentWebSocketSessionDecorator(session, 5000, 65536);
    guarded.setTextMessageSizeLimit(1024);
    boolean admitted;
    synchronized (connections) {
      admitted =
          connections.size() < 64
              && connections.values().stream()
                      .filter(value -> value.proof().employeeId().equals(proof.employeeId()))
                      .count()
                  < 3;
      if (admitted) {
        connections.put(session.getId(), new Connection(guarded, proof));
      }
    }
    if (!admitted) {
      session.close(CloseStatus.POLICY_VIOLATION);
      return;
    }
    guarded.sendMessage(
        new TextMessage(json.writeValueAsString(Map.of("type", "READY", "protocol", PROTOCOL))));
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    var connection = connections.get(session.getId());
    if (connection == null || !authorized(connection) || !message.getPayload().equals("ping")) {
      close(session);
      return;
    }
    connection
        .session()
        .sendMessage(new TextMessage(json.writeValueAsString(Map.of("type", "PONG"))));
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    connections.remove(session.getId());
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable failure) {
    close(session);
  }

  @Override
  public Outcome send(Notice notice) {
    var message =
        new TextMessage(
            json.writeValueAsString(
                Map.of(
                    "id",
                    notice.id(),
                    "sequence",
                    notice.sequence(),
                    "orderId",
                    notice.orderId(),
                    "type",
                    notice.kind().name(),
                    "occurredAt",
                    notice.occurredAt())));
    int sent = 0;
    int failed = 0;
    for (var connection : connections.values()) {
      if (!authorized(connection)) {
        continue;
      }
      try {
        connection.session().sendMessage(message);
        sent++;
      } catch (IOException | RuntimeException exception) {
        failed++;
        close(connection.session());
      }
    }
    return new Outcome(sent, failed);
  }

  @Override
  public void prune() {
    for (var connection : connections.values()) {
      authorized(connection);
    }
  }

  private boolean authorized(Connection connection) {
    try {
      if (connection.session().isOpen() && sessions.active(connection.proof())) {
        return true;
      }
    } catch (RuntimeException exception) {
      // 认证依赖故障时也关闭连接，不能沿用旧权限。
      close(connection.session());
      return false;
    }
    close(connection.session());
    return false;
  }

  private void close(WebSocketSession session) {
    connections.remove(session.getId());
    try {
      session.close(CloseStatus.POLICY_VIOLATION);
    } catch (IOException ignored) {
      // 已关闭连接无需再次处理。
      return;
    }
  }

  /** 会话证明自身已脱敏，连接集合只保存在当前进程内. */
  private record Connection(WebSocketSession session, StaffSessions.Proof proof) {}
}
