package com.hanserwei.hanmenu.notification.web.admin;

import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import com.hanserwei.hanmenu.notification.application.NotificationService;
import com.hanserwei.hanmenu.notification.application.StreamTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 员工通知资源提供补查、个人确认和一次性长连接票据，重投操作要求管理员. */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "员工通知")
class NotificationController {
  private final NotificationService notifications;
  private final StreamTicketService tickets;

  NotificationController(NotificationService notifications, StreamTicketService tickets) {
    this.notifications = notifications;
    this.tickets = tickets;
  }

  /** 按最后确认游标补查提交后的消息，断线不丢通知. */
  @GetMapping
  @Operation(summary = "按游标补查来单和催单通知")
  NotificationService.Feed feed(
      @AuthenticationPrincipal StaffIdentity actor,
      @RequestParam(defaultValue = "0") @Min(0) long after,
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
    return notifications.feed(actor, after, limit);
  }

  /** 返回本人持久化阅读进度. */
  @GetMapping("/receipt")
  @Operation(summary = "读取本人通知阅读游标")
  NotificationService.ReceiptView receipt(@AuthenticationPrincipal StaffIdentity actor) {
    return notifications.receipt(actor);
  }

  /** 显式确认阅读，WebSocket 发送成功不会自动推进该游标. */
  @PutMapping("/receipt")
  @Operation(summary = "按版本前移本人阅读游标")
  NotificationService.ReceiptView acknowledge(
      @AuthenticationPrincipal StaffIdentity actor, @Valid @RequestBody Acknowledge body) {
    return notifications.acknowledge(actor, body.sequence(), body.version());
  }

  /** 票据必须经员工 Bearer 签发，并通过子协议携带而非 URL 参数. */
  @PostMapping("/stream-tickets")
  @Operation(summary = "签发三十秒一次性通知连接票据")
  ResponseEntity<StreamTicketService.Ticket> ticket(@RequestHeader("Authorization") String bearer) {
    return ResponseEntity.status(201)
        .cacheControl(CacheControl.noStore())
        .body(tickets.issue(bearer.substring(7).strip()));
  }

  /** 管理员查询有限投递尝试记录. */
  @GetMapping("/{id}/attempts")
  @Operation(summary = "查询通知投递轨迹")
  List<NotificationService.AttemptView> attempts(
      @AuthenticationPrincipal StaffIdentity actor, @PathVariable UUID id) {
    return notifications.attempts(actor, id);
  }

  /** 只重投已耗尽的消息，客户端仍按原游标去重. */
  @PostMapping("/{id}/redelivery")
  @Operation(summary = "管理员重试投递耗尽的通知")
  NotificationService.NoticeView retry(
      @AuthenticationPrincipal StaffIdentity actor,
      @PathVariable UUID id,
      @Valid @RequestBody Version body) {
    return notifications.redeliver(actor, id, body.version());
  }

  /** 阅读确认必须带当前个人游标版本. */
  record Acknowledge(@NotNull @Min(0) Long sequence, @NotNull @Min(0) Long version) {}

  /** 消息重投必须携带最新版本. */
  record Version(@NotNull @Min(0) Long version) {}
}
