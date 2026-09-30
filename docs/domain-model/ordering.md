# ordering —— 订单与履约

代码位置：`src/main/java/com/hanserwei/hanmenu/ordering/`

## 职责与边界

- 维护订单从提交、支付、履约到完成/取消的完整状态机。
- 结算时通过 customer（地址）、shop（营业状态）、catalog（计价）、cart（扣减）的公开 API 协作，
  通过 `PaymentOperations` 发起支付，通过 payment 的结果事件收尾。
- 不依赖支付模块内部类型；payment 不反向依赖本模块。

## 聚合根：Order

订单聚合维护付款、取消与履约状态；**金额和收货快照终身不变**。

### 状态机

`Status`：`UNPAID → PAID → ACCEPTED → DELIVERING → COMPLETED`（履约主线）

取消支线（按是否有支付意图分流）：

- `UNPAID`（无支付意图）→ 直接终态 `CANCELLED`
- `UNPAID`（有支付意图）→ `CANCELLING`（等待渠道关单）→ `CANCELLED`
- `PAID` / `ACCEPTED` → `REFUNDING`（等待退款确认）→ `CANCELLED`

| 状态 | 含义 |
| --- | --- |
| `UNPAID` | 已提交未支付，固定 15 分钟支付窗口（`expiresAt`，不可延长）。 |
| `PAID` | 真实付款成功，等待接单。 |
| `ACCEPTED` / `DELIVERING` / `COMPLETED` | 商家接单 / 配送中 / 已完成。 |
| `CANCELLING` | 已请求取消且存在支付意图，等待渠道关单。 |
| `REFUNDING` | 已付款后取消，等待退款确认（`RefundStatus.PENDING`）。 |
| `CANCELLED` | 取消终态（含超时、关单、退款完成）。 |

`CancelReason`：`CUSTOMER`、`TIMEOUT`、`MERCHANT_REJECTED`、`MERCHANT_CANCELLED`、
`PAYMENT_CLOSED`（固定业务枚举，不保存敏感自由文本）。
各原因允许的起点：CUSTOMER→UNPAID/PAID；TIMEOUT→UNPAID 且已过支付窗口；
MERCHANT_REJECTED→PAID；MERCHANT_CANCELLED→PAID/ACCEPTED；
PAYMENT_CLOSED 仅由渠道关单事件路径写入，不可直接请求。

`RefundStatus`：`NONE`、`PENDING`、`SUCCEEDED` —— 已取消订单的迟到付款可独立显示退款处理中，
**不恢复订单状态**；`SUCCEEDED` 表示退款已确认。

### 业务方法

| 业务方法 | 说明 |
| --- | --- |
| `submit(customerId, key, fingerprint, address, lines, now)` | 提交订单（幂等 key + 请求指纹 + 地址快照 + 条目）。 |
| `cancel(expectedVersion, now)` | 顾客在接单前申请取消；委托 `requestCancellation(CUSTOMER)`。 |
| `requestCancellation(expectedVersion, reason, now)` | 保存取消意图并按支付意图分流；**不把网络受理或结果未知当作取消完成**。 |
| `attachPayment(id, expectedVersion, now)` | 关联支付单。 |
| `paymentSucceeded(payment, amount, paymentTime, now)` | 真实付款成功 → `PAID`。 |
| `paymentClosed(payment, amount, now)` | 渠道关单：未付款且处于 UNPAID/CANCELLING 时终态取消（原因默认 `PAYMENT_CLOSED`）。 |
| `refundSucceeded(payment, refund, amount, now)` | 退款确认，终态取消。 |
| `accept` / `deliver` / `complete` | 履约推进（员工操作，均带乐观版本）。 |
| `remind(expectedVersion, now)` | 顾客催单（有界频次）。 |
| `requireSameRequest(fingerprint)` / `requireVersion(expected)` | 幂等与乐观锁校验。 |

`Lifecycle` record 持久化生命周期快照（paymentId、各时刻、取消原因、退款状态），
行为仍由聚合维护。

## 值对象与实体

| 类型 | 约束 |
| --- | --- |
| `OrderLine(id, productId, kind, name, unitPrice, quantity, selections, components)` | 成交快照：商品、规格、套餐组成与人民币价格，**后续不可重新计价**。 |
| `OrderLine.Component(productId, name, quantity, selections)` | 套餐固定组成不随原菜品名称或口味修改。 |
| `AddressSnapshot(sourceId, sourceVersion, recipientName, phone, 省/市/区/detail)` | 不可变收货资料，与地址簿后续修改或删除隔离。 |
| `OrderPage.Summary` | 列表最小信息；历史列表不读取收货资料，避免查询放大。 |
| `OrderSearch` | 后台组合条件：状态/订单号/顾客/电话（匹配历史收货快照）/创建时间左闭右开区间。 |

## 端口

| 端口 | 职责 |
| --- | --- |
| `OrderRepository` | 只暴露聚合与有界查询，不导出持久化实体。 |

## 领域异常

`OrderException.Reason`：`INVALID_INPUT`、`NOT_FOUND`、`SHOP_CLOSED`、
`VERSION_CONFLICT`、`STATE_CONFLICT`（状态迁移冲突）、`IDEMPOTENCY_CONFLICT`（幂等冲突）。

## 公开契约与集成事件

`api` 包：

- `OrderFacts` —— 统计专用无个人资料订单快照（`Snapshot` 含源版本 `version`、
  状态时刻、条目 `Line`）；统计重建通过该 API 读取而非访问订单表。

`events` 包（与订单状态变更同事务登记）：

- `OrderChanged(snapshot)` —— 完整统计快照事件；消费者用订单版本拒绝重复和乱序。
- `OrderReady(id, orderId, occurredAt)` —— **只有真实付款使订单进入待接单时**才产生来单事实；
  取消后迟到付款不产生来单。
- `OrderReminderRaised(id, orderId, count, occurredAt)` —— 顾客合法催单事实，
  标识由订单与催单次数决定，重复投递可安全去重。

## 关键业务规则

1. 状态迁移只能由聚合行为完成，不提供任意状态 setter。
2. 提交幂等：同 `idempotencyKey` + `requestFingerprint` 的重复请求返回同一订单。
3. 金额与收货快照在下单时冻结；目录、地址簿后续变化不影响历史订单。
4. 支付窗口固定 15 分钟（按 `createdAt` 计算），超时取消（`CancelReason.TIMEOUT`），
后续支付创建或重试不能延长窗口。
5. 取消按支付意图分流：未付款但有支付意图走 `CANCELLING`（等渠道关单），
已付款走 `REFUNDING`（等退款确认）；迟到付款对已取消订单只登记退款，不恢复订单。
6. 催单有频次上限（`reminderCount` / `lastRemindedAt`）。

## 相关文档

- [P5_CONTRACT.md](../P5_CONTRACT.md)（订单与支付履约契约）
- [P7_BACKEND_CONTRACT.md](../P7_BACKEND_CONTRACT.md)（订单履约允许员工操作）
- [payment.md](payment.md)（结果事件协作）
- 数据库：`V5__unpaid_orders.sql`、`V6__payments_and_fulfillment.sql`
