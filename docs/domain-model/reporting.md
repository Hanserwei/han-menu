# reporting —— 经营报表投影

代码位置：`src/main/java/com/hanserwei/hanmenu/reporting/`

## 职责与边界

- 基于公开事件/API 建立可重建的经营统计投影，不访问其他模块业务表。
- 消费 ordering 的 `OrderChanged`、customer 的 `CustomerRegistered`、
  payment 的 `PaymentResult`/`RefundResult` 事件，并可经 `OrderFacts`/`PaymentFacts`
  全量重建。
- 统计模块自己的事实模型（`ReportingFacts`）不引用其他模块聚合或 ORM 实体。

## 聚合根：ProjectionState

投影控制聚合，串行化事件更新和原子重建。

| 业务方法 | 说明 |
| --- | --- |
| `requireReady()` | 投影未初始化时拒绝读取（**不能被误读为零营业数据**）。 |
| `requireVersion(expected)` | 乐观锁校验。 |
| `applied(now)` | 事件增量应用后推进修订号。 |
| `rebuilt(now)` | 全量重建完成，切换可见代际。 |

状态：`version`（乐观锁）、`generation`（重建代际）、`revision`（修订号）、
`initialized`、`updatedAt`、`rebuiltAt`。

## 时间口径：BusinessTime

单店经营日期统一按**上海时区**，源事件与数据库快照统一至 PostgreSQL 微秒精度。

- `canonical(value)`：规范化时刻。
- `date(value)`：时刻所属经营日。

## 事实模型与值模型

### ReportingFacts（模块内事实）

| 类型 | 约束 |
| --- | --- |
| `Order`（含 `newerThan(previousVersion)`） | 订单投影按**源版本**更新，历史明细和金额不由统计模块重新定价。 |
| `Order.Line` | 销量按稳定商品标识合并，单价来自成交快照。 |
| `Customer(id, createdAt)` | 增长统计不保存顾客个人资料。 |
| `Receipt(id, orderId, amount, paidAt)` | 已确认收款，**重复事件不能重复增加营业资金**。 |
| `Refund(id, paymentId, orderId, amount, confirmedAt)` | 已确认退款，受理或重试状态不形成资金支出。 |

### ReportData（报表值模型）

口径明确区分三类日账：

| 类型 | 口径 |
| --- | --- |
| `OrderDay` | **按创建日**的订单群组：提交数、当前完成数、取消数（群组口径）。 |
| `CompletionDay` | **按完成日**的交付订单数与营业额。 |
| `CashDay` | **按确认事实发生日**计资金（收款按渠道付款时刻、退款按服务端确认时刻），不以订单状态代替资金记录。 |
| `CustomerDay` | 按注册日新增顾客数。 |
| `Day` | 补齐零值日期的统一日账，金额精确到分。 |
| `Sale` | 商品销量与金额按商品 UUID 合并，名称取最近订单快照。 |
| `Reconciliation` | 资金对账差异计数（缺单/金额不符/缺收款/缺退款/待退款），**只报告不篡改业务事实**。 |
| `Summary` | 总览汇总（含完成率、净收款、客单价），导出与 HTTP 使用同一批数据。 |

### 导出模型

| 类型 | 约束 |
| --- | --- |
| `ReportPeriod(from, to)` | 首尾均包含的有界区间，**最多 366 天**。 |
| `ReportWorkbook` | 报表与导出共用的不可变一致性快照（含 `Metadata` 版本元信息）；文件生成不再读数据库。 |
| `ReportExporter`（端口） | 将已读取完毕的快照导出为工作簿，不依赖 HTTP、数据库或其他业务模块。 |

## 端口

| 端口 | 职责 |
| --- | --- |
| `ReportingRepository` | 仅操作本模块投影表；源业务数据必须通过公开快照契约获取。 |
| `ReportExporter` | 报表文件导出端口。 |

## 领域异常

`ReportingException.Reason`：`INVALID_INPUT`、`VERSION_CONFLICT`、`CONFLICT`、
`NOT_READY`（未初始化的投影不能被误读为零营业数据）、`UNAVAILABLE`。

## 公开契约与集成事件

`api` / `events` 包：当前无对外同步契约与集成事件（统计是终态消费方）。

消费的事件：

- `OrderChanged`（ordering）—— 按订单版本幂等更新订单投影。
- `CustomerRegistered`（customer）—— 新增顾客计数。
- `PaymentResult` / `RefundResult`（payment）—— 已确认资金事实。

## 关键业务规则

1. 投影可全量重建：事件按业务标识 + 源版本幂等；`Receipt`/`Refund` 防止重复计资金。
2. 三种口径不混用：创建群组（订单漏斗）、完成日（营业额）、确认日（资金）。
3. 未初始化（`NOT_READY`）≠ 零营业；读取前必须 `requireReady`。
4. 重建与增量更新经 `ProjectionState` 串行化，代际切换保证读到一致快照。
5. 导出与页面展示来自同一 `ReportWorkbook` 快照，保证口径一致。

## 相关文档

- [P6_CONTRACT.md](../P6_CONTRACT.md)（报表与对账接口契约）
- [PC5_CONTRACT.md](../PC5_CONTRACT.md)（管理端经营分析与导出）
- [ordering.md](ordering.md)、[payment.md](payment.md)（事实来源）
- 数据库：`V7__notifications_and_reporting.sql`、`V8__management_queries.sql`
