# notification —— 员工消息通知

代码位置：`src/main/java/com/hanserwei/hanmenu/notification/`

## 职责与边界

- 持久化员工通知（来单、催单），通过 WebSocket 在线推送，支持断线补查与阅读确认。
- 通知通过公开订单事件持久化（消费 ordering 的 `OrderReady`、`OrderReminderRaised`）。
- WebSocket 连接使用一次性票据并复验员工会话（identity 的 `StaffSessions`）。

## 聚合根

### Notice

持久化通知聚合，维护有界重试；客户端断线时通过提交游标补查并确认阅读。

`Kind`（固定通知类型，不保存用户输入的消息文本或收货信息）：
`NEW_ORDER`（来单）、`ORDER_REMINDER`（催单）

`Status`：`PENDING`（待投递）→ `IN_FLIGHT`（投递中）→ `DELIVERED`（已投递）；
重试耗尽后为 `EXHAUSTED`（仍可补查）

| 业务方法 | 说明 |
| --- | --- |
| `create(id, sequence, orderId, kind, occurredAt, now)` | 从订单事件创建通知；`sequence` 为全局单调序号。 |
| `claim(now)` | 领取投递尝试（返回 attemptId），防止并发重复发送。 |
| `finish(attemptId, success, failure, now)` | 记录投递结果。 |
| `retry(expected, now)` | 有界重试（attempts/failures/nextAttemptAt）。 |

投递状态与员工阅读确认（`Receipt`）相互独立；重试耗尽不删除可补查历史。

### Receipt

每位员工独立的阅读进度聚合。

| 业务方法 | 说明 |
| --- | --- |
| `acknowledge(target, expectedVersion, head, now)` | 确认已读至目标序号；`head` 为当前最大序号。 |

版本和单调性防止多设备用旧进度覆盖新进度。

### StreamTicket

一次性长连接票据。

| 业务方法 | 说明 |
| --- | --- |
| `consume(now)` | 建立连接时消费票据（一次性）。 |

只保存摘要（`ticketHash`），绑定原始员工会话（`sessionHash`）与 `securityVersion`，
过期即失效。

## 值对象

| 值对象 | 约束 |
| --- | --- |
| `NotificationPush.Outcome(sent, failed)` | 在线提示投递结果；**没有在线订阅者不等于投递成功**。 |
| `NotificationRepository.Attempt` | 投递尝试只记录内部编号、结果和固定失败分类。 |

## 端口

| 端口 | 职责 |
| --- | --- |
| `NotificationRepository` | 持久化消息、领取记录、阅读进度和一次性票据。 |
| `NotificationPush` | 在线提示投递端口；适配器**每次发送前重验会话**，网络写入不占数据库事务。 |

## 领域异常

`NotificationException.Reason`：`INVALID_INPUT`、`NOT_FOUND`、`VERSION_CONFLICT`、`CONFLICT`、
`INVALID_CREDENTIALS`（不携带凭证或发送报文）。

## 公开契约与集成事件

`api` / `events` 包：当前无对外同步契约与集成事件（通知是终态消费方）。

消费的事件（ordering 发布）：

- `OrderReady` —— 来单通知（`Kind.NEW_ORDER`）。
- `OrderReminderRaised` —— 催单通知（`Kind.ORDER_REMINDER`，按订单+次数幂等去重）。

## 关键业务规则

1. 通知由订单事件驱动、先持久化再投递；推送失败不影响通知事实。
2. WebSocket 建连必须持一次性有效票据，并复验员工会话与安全版本。
3. 每次在线推送前重验会话，防止已撤销会话继续收消息。
4. 阅读进度按员工独立维护，游标单调递增，多设备不可回退覆盖。
5. 重试有界（attempts 上限）；耗尽后进入 `EXHAUSTED`，仍可通过补查 API 拉取历史。

## 相关文档

- [P6_CONTRACT.md](../P6_CONTRACT.md)（通知与报表接口契约）
- [PC4_CONTRACT.md](../PC4_CONTRACT.md)（管理端实时通知与阅读恢复）
- [identity.md](identity.md)（会话复验）
- 数据库：`V7__notifications_and_reporting.sql`
