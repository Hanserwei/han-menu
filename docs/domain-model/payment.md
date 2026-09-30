# payment —— 支付与退款（支付宝沙箱）

代码位置：`src/main/java/com/hanserwei/hanmenu/payment/`

## 职责与边界

- 维护支付单与全额退款单两个聚合，渠道为支付宝沙箱。
- 渠道签名和验签在服务端；**不能信任 App 返回的支付成功**。
- 远程渠道调用（查询、关单、退款）只在**数据库事务之外**执行，通过聚合状态机串行化重试。
- 不反向依赖 ordering：业务引用 `businessRef` 为不透明 UUID。

## 聚合根

### Payment

支付聚合维护**单调渠道事实**：状态只前进不回退。

`Status`：`PENDING`（待支付）→ `SUCCEEDED`（已付款）/ `CLOSED`（已关闭）

| 业务方法 | 说明 |
| --- | --- |
| `create(businessRef, customerId, amount, key, fingerprint, now, expiresAt)` | 创建支付单（幂等 key + 请求指纹 + 过期时刻）。 |
| `claim(now)` | 领取处理权（外部查询/关单任务），失败者不重复执行。 |
| `retry(now)` | 渠道调用失败后安排重试（`nextAttemptAt`）。 |
| `observe(TradeResult, now)` | 记录已验证的渠道事实；**关闭后收到真实成功仍记账**并交给订单补偿。 |
| `requestClose(now)` | 请求关单（未支付订单超时后）。 |
| `request()` | 构造 `TradeRequest`（渠道请求值对象）。 |
| `requirePayable(now)` / `requireSameRequest(expected)` / `requireVersion(expected)` | 可付性 / 幂等 / 乐观锁校验。 |

不变量：**已付款不可被延迟到达的关闭或等待通知覆盖**；
"渠道查不到交易"不等于支付已关闭（签名参数仍可能在有效期内创建交易）。

### Refund

全额退款聚合以**固定退款号**实现渠道幂等，只有查询确认后才完成。

`Status`：`PENDING`（处理中，含受理/异常/结果不明）→ `SUCCEEDED`（已确认）

| 业务方法 | 说明 |
| --- | --- |
| `create(payment, now)` | 从已付款支付单创建全额退款（退款号 = 聚合标识）。 |
| `claim(now)` / `retry(now)` | 领取与重试，同 Payment 模式。 |
| `confirm(now)` | 渠道查询确认成功后置为 `SUCCEEDED`。 |
| `request()` | 构造 `RefundRequest`（含稳定业务退款号，网络重试不能重复退款）。 |

## 值对象

| 值对象 | 约束 |
| --- | --- |
| `PaymentGateway.TradeRequest(paymentId, amount, expiresAt)` | 渠道请求只含内部交易引用、人民币金额和固定过期时刻。 |
| `PaymentGateway.TradeResult(state, tradeNo, amount, paidAt)` | 已验证的渠道交易事实，不带 SDK 对象。 |
| `PaymentGateway.State`：`NOT_FOUND`、`PENDING`、`SUCCEEDED`、`CLOSED` | 渠道查询状态。 |
| `PaymentGateway.RefundRequest(refundId, paymentId, tradeNo, amount)` | 全额退款请求。 |
| `PaymentGateway.Notice(paymentId, result)` | 验签后的通知，仍须与数据库支付单金额、渠道号核对。 |
| `TransactionSearch` / `TransactionPage` | 管理员交易检索（业务引用为订单 UUID）。 |

## 端口

| 端口 | 职责 |
| --- | --- |
| `PaymentGateway` | 支付渠道端口；远程调用在数据库事务之外。 |
| `PaymentRepository` | 支付和退款仓储；写操作参与调用方短事务。 |

## 领域异常

`PaymentException.Reason`：`INVALID_INPUT`、`NOT_FOUND`、`VERSION_CONFLICT`、
`CONFLICT`、`INVALID_NOTIFICATION`、`UNAVAILABLE`（渠道故障）
（不包含渠道原始报文或密钥）。

## 公开契约与集成事件

`api` 包：

- `PaymentOperations` —— 订单使用的支付能力；`Intent` 为订单持有的最小支付引用
  （id、status、expiresAt、replayed）。
- `AppPayment` —— App 侧支付参数：**只拿到沙箱签名参数（orderString 等），
  参数本身不能证明付款成功**。
- `PaymentFacts` —— 已确认资金事实的统计与重建契约：
  `Receipt`（原收款按渠道付款时刻计入）、`Refund`（退款按服务端确认时刻计入）。

`events` 包（与支付状态同事务登记）：

- `PaymentResult(paymentId, businessRef, customerId, amount, status, paidAt, occurredAt)` ——
  已持久化的渠道确认结果；不含个人资料或渠道原文。
- `RefundResult(refundId, paymentId, businessRef, amount, confirmedAt)` ——
  已确认全额退款事件，消费者按退款标识与聚合状态幂等处理。

## 关键业务规则

1. 渠道事实单调：`SUCCEEDED` 终态不可被迟到通知覆盖；迟到成功发生在关单后也要记账，
   由订单侧补偿（见 ordering 的迟到付款规则）。
2. 幂等三件套：支付单幂等 key + 请求指纹；退款固定业务退款号；事件消费方按标识幂等。
3. 外部支付请求不放入长数据库事务；先落状态（claim）再调渠道，失败 retry。
4. 异步通知必须验签，且与库内支付单核对金额与渠道单号后才能 `observe`。
5. App 侧永远无法证明付款成功；真相只来自服务端渠道查询/通知。

## 相关文档

- [P5_CONTRACT.md](../P5_CONTRACT.md)（支付与退款接口契约）
- [MOBILE_PAYMENT_DECISION.md](../MOBILE_PAYMENT_DECISION.md)（移动支付决策）
- [ordering.md](ordering.md)（订单侧协作）
- 数据库：`V6__payments_and_fulfillment.sql`
