# P6：可靠通知、鉴权 WebSocket 与经营统计

## 范围与模块边界

P6 实现顾客催单、员工来单／催单通知、投递跟踪与重试、个人阅读游标、鉴权 WebSocket、工作台、
经营日账、顾客增长、商品销量、资金对账、XLSX 导出及统计投影重建。当前仍是单店、单个可执行
JAR；Flutter 和新管理端界面属于 P7，没有提前创建前端工程、短信服务或外部消息中间件。

ordering 在原业务事务中发布订单统计快照、付款后待接单事实和合法催单事实；customer 发布最小
注册事实；payment 沿用已确认支付及退款事件。notification 只依赖 ordering 的公开事件和 identity
的公开身份契约。reporting 通过公开事件和 ordering/customer/payment 的快照 API 建立自己的
投影，工作台通过 catalog/shop API 获取当前摘要。没有跨模块表查询、实体导入或外键。

通知、票据、阅读进度和投影控制都有明确的领域行为或不可变事实模型；基础设施使用 JPA/Hibernate，
普通查询使用派生方法，组合条件复用 Specification，分组聚合由 JPA Criteria 完成。应用层不执行 JDBC。

## 顾客催单

新增 POST `/api/v1/orders/{id}/reminders`，顾客 Bearer，body 为 `{"version":当前订单版本}`，
成功返回 200 最新订单详情。详情新增 reminderCount 与 lastRemindedAt。

仅 PAID、ACCEPTED、DELIVERING 可催单；未付款、取消处理中、退款中、已取消或已完成均拒绝。
两次成功催单至少间隔六十秒，版本和冷却窗口均在订单行锁内验证。他人订单与不存在订单统一 404；
旧版本、错误状态或冷却窗口内重复请求返回 409。请求不接受消息正文、目标员工或顾客标识。

催单状态与事件登记一同提交或回滚。事件标识由订单和催单次数确定，重复投递不会重复建立消息。
来单事件仅在真实付款使 UNPAID 转为 PAID 时产生；取消后的迟到付款不会产生新的待接单提示。

## 员工通知 HTTP 契约

ADMIN 和 STAFF 均可读取本店消息、签发连接票据及确认个人阅读进度。查看投递轨迹和重投要求
当前有效管理员。HTTP 使用员工 Bearer；顾客、匿名、到期或已撤销员工会话不能访问。

| 方法与路径 | 请求 | 响应 |
| --- | --- | --- |
| GET `/api/v1/notifications?after=0&limit=50` | after 为已处理游标；limit 1—100 | items/nextCursor/hasMore |
| GET `/api/v1/notifications/receipt` | 当前员工 | sequence/version/updatedAt |
| PUT `/api/v1/notifications/receipt` | `{"sequence":12,"version":1}` | 当前个人阅读进度与新版本 |
| POST `/api/v1/notifications/stream-tickets` | 员工 Bearer，无业务请求体 | 201 ticket/expiresAt/protocol，Cache-Control: no-store |
| GET `/api/v1/notifications/{id}/attempts` | 管理员 | 最近最多 100 条尝试记录 |
| POST `/api/v1/notifications/{id}/redelivery` | 管理员，`{"version":当前通知版本}` | 重投后的消息及新版本 |

消息字段为 id、sequence、orderId、type、occurredAt、deliveryStatus、attempts、lastFailure、version。
type 为 NEW_ORDER 或 ORDER_REMINDER。消息不含地址、电话、姓名、凭证或任意外部文本。
尝试记录包含尝试 UUID、开始／结束时间、状态、成功与失败连接数和固定失败分类。

sequence 来自同事务提交顺序控制行，**不是**可提前分配并乱序提交的数据库序列。两个并发写入
会串行分配并提交游标，客户端不会读到更大的游标后再遗漏尚未提交的小游标。业务回滚不会消耗
可见游标。每页 nextCursor 只取本页实际返回的最后一项，不跳到另外查询到的全局最大值。

个人阅读进度按员工 UUID 隔离，默认 sequence=0/version=0。第一次前移持久化版本为 1；后续
更新必须提供当前版本，不能倒退或越过已提交消息头。服务器发送成功不自动确认员工已阅读。
不同设备同时确认时，旧版本返回 409；客户端应重读本人进度。越界或倒退返回 400。

## WebSocket 协议与安全

连接路径为 `/api/v1/notifications/stream`，生产环境使用 WSS。浏览器先通过 Bearer HTTP 签发
票据，再将以下两个子协议交给 WebSocket 构造器：

```javascript
new WebSocket(streamUrl, [ticket.protocol, `ticket.${ticket.ticket}`]);
```

固定业务协议为 `han-menu.notifications.v1`。票据由 256 位随机数生成，带 `hmw_` 前缀，三十秒
有效、只能消费一次。表中只保存摘要，绑定原员工会话摘要和账号安全版本；每个原会话只保留最新
未到期票据。并发首次签发冲突可以重试，重新签发会使该会话上一个未使用的票据失效。

握手先验证 Origin，再通过数据库行锁消费票据并重验原会话。所有 URL 查询参数均拒绝，避免把
凭证放入 URL；服务端只回显固定业务协议，不回显票据子协议。默认同源；如确有跨来源部署需求，
`NOTIFICATION_ALLOWED_ORIGINS` 配置逗号分隔的明确 HTTP/HTTPS Origin，不允许通配符。
无 Origin 的 Flutter／非浏览器客户端仍须通过同样的员工票据认证。

连接建立后收到 `{"type":"READY","protocol":"han-menu.notifications.v1"}`。实时消息仅包含
id/sequence/orderId/type/occurredAt。客户端可发送文本 `ping`，合法会话返回 `{"type":"PONG"}`；
连接不接收任意业务命令。文本消息上限 1024 字节，每员工最多三个连接，当前进程总共最多六十四个。
发送适配器使用并发发送保护、五秒发送时限和 64 KiB 缓冲上限。

每次发送或响应 ping 前都向 identity 公开契约重新检查原数据库会话；另有独立的五秒空闲检查。
退出、会话到期、禁用或改密使旧连接被关闭。认证依赖故障也关闭连接，不沿用旧权限。数据库事务
在网络发送前结束；接收方断线不回滚订单或抹除持久化消息。

**客户端恢复顺序：**建立连接后读取本人 receipt，再按该游标调用通知补查，直到 hasMore=false；
成功处理完整连续页面后，按版本前移阅读进度。实时帧只是触发补查的提示，可能重复或乱序，
不能直接把实时帧的 sequence 当成已完整处理游标，否则可能跳过之前失败投递的消息。客户端应按
id/sequence 去重，并在重连及定期恢复时重复补查。通知描述发生过的事实，履约前仍须读取当前订单。

当前实时连接注册表属于单进程，符合现阶段单体部署范围。数据库消息可持久化恢复，但没有宣称
跨实例 WebSocket 广播；多实例推送和前端恢复界面在 P7 部署／客户端阶段按实际需要继续实现。

## 投递重试与可靠事件

事件消费者仅落库消息，不在事件事务中写网络。通知工作器先在短事务中领取，再在事务外广播，
最后在另一短事务中登记结果。投递状态为 PENDING、IN_FLIGHT、DELIVERED、EXHAUSTED。
DELIVERED 只代表已写入当前在线合法连接，不代表所有员工已经阅读。

没有在线订阅者记录 NO_SUBSCRIBERS；网络故障记录 SEND_FAILED。普通失败重试间隔为
5、10、20、40 秒，连续五次失败后耗尽。耗尽消息仍可通过 HTTP 补查，管理员可按版本重投，
累计尝试数和旧轨迹保留。每次领取有独立 UUID 和六十秒恢复窗口；进程退出后可重新领取，旧领取
标为 EXPIRED，迟到结果不能覆盖新的投递状态。一次扫描最多一百条待投递消息。

Modulith 事件登记继续与源业务状态同事务。所有消费者均幂等，失败登记保留，启动可重投；周期
恢复已从支付轮询配置中移到独立的应用级配置，`EVENT_RECOVERY_ENABLED` 默认 true。
恢复采用一分钟最小年龄、100 条批次、最多 10 项同时处理。关闭支付轮询不会关闭通知或统计的事件恢复。

`NOTIFICATION_SCHEDULING_ENABLED` 默认 true，控制实时消息投递和空闲连接检查；关闭时应由
受控用例显式调度，不应用于正常运行的员工长连接。调度器有四个工作线程，避免一个慢渠道阻塞
全部本地维护任务。测试显式关闭定时工作器，直接驱动并验证同样的事务与真实网络适配器。

## 工作台与经营 HTTP 契约

| 方法与路径 | 权限／输入 | 用途 |
| --- | --- | --- |
| GET `/api/v1/workspace` | 当前有效 ADMIN/STAFF | 当前经营日订单待办、门店状态、菜品／套餐上下架数量 |
| GET `/api/v1/reports/operations?from=2026-09-01&to=2026-09-30` | ADMIN | 经营汇总、订单群组完成率和逐日顾客增长 |
| GET `/api/v1/reports/sales?from=...&to=...&limit=10` | ADMIN；limit 1—100 | 已完成订单商品销量排行 |
| GET `/api/v1/reports/reconciliation?from=...&to=...` | ADMIN | 实际收退款、净收款及双向事实差异 |
| GET `/api/v1/reports/export?from=...&to=...` | ADMIN | 标准 XLSX，含汇总、日账、销量前 100 和资金对账 |
| GET `/api/v1/reports/projection` | ADMIN | initialized/version/generation/revision、时间和记录数 |
| POST `/api/v1/reports/projection/rebuild` | ADMIN；`{"version":当前控制版本}` | 从公开源快照原子重建 |

from/to 为包含首尾的 ISO 日期，最多连续 366 天，年份范围为 1970—9999。默认经营时区固定为
Asia/Shanghai，所有事件／业务时刻仍为 UTC Instant。经营汇总明确返回 zone、currency=CNY，各报表附投影
代际、修订号和更新时间；异步投影可能短暂落后，不能用它绕过权威订单状态与版本校验。

工作台对所有日期的 PAID、ACCEPTED、DELIVERING、CANCELLING、REFUNDING 给出当前待办数量，
另含当前经营日的创建／完成数量。门店和目录摘要来自公开 API 的实时数据库查询，不依赖展示缓存。
普通员工的工作台不包含财务金额或顾客个人资料。

## 统计口径与金额对账

- **营业额 turnover：**当前 COMPLETED 订单按 completedAt 所属经营日汇总成交总金额。
- **completedOrders：**按完成日计算完成订单数，包含以前日期创建而在当日完成的订单。
- **submittedOrders：**按 createdAt 所属经营日计算创建订单数。
- **completedCohort/cancelledCohort：**该创建日期群组里当前已完成／已取消订单数；取消处理中不算已取消。
- **completionRatePercent：**同一创建群组的已完成数 / 创建数，乘 100，保留两位小数；零分母返回 0.00。
- **averageOrderValue：**已完成营业额 / 按完成日计数的完成订单数；零完成返回 0.00。
- **receivedAmount：**只计已确认 SUCCEEDED 支付，按渠道付款时刻所属经营日确认。
- **refundedAmount：**只计已查询确认 SUCCEEDED 的退款，按服务端退款确认时刻所属经营日确认。
- **netReceivedAmount：**收款减已确认退款；跨日退款可使某日为负，与营业额不是同一个指标。
- **newCustomers/累计顾客：**按注册日统计，包含已停用账号的历史注册事实，不代表日活或付费人数。

空日期会补零；期末累计顾客包含查询起日之前的注册。订单完成率不会用不同日期口径的两个计数相除。
销量只使用完成订单成交明细，按商品 UUID 合并，名称取最近订单快照；改名不会拆开同一商品。
排序为数量降序、金额降序、UUID 升序，历史目录删除不影响统计。套餐按套餐商品计件，不重复计入组成菜品。

对账正向检查区间内收款是否有订单、金额／支付引用是否相符，以及退款是否有原支付、订单／金额
是否一致。另有全店当前的反向检查：已付款订单是否缺少收款事实，已退款订单是否缺少匹配退款事实；
pendingRefunds 是全店当前待退款数，不按日期截断。差异项可能重叠，短暂事件滞后也可能形成差异，
必须结合投影版本与重建结果诊断，报表不修改订单、补造付款或把退款受理当作资金支出。

报表查询采用本模块索引和分组聚合，日期数不会变成逐日查询次数；没有读取全部订单到 Java 后统计。
多项统计使用同一 REPEATABLE_READ 快照，不混合重建前后的数据。

## XLSX 导出

导出包含「口径与汇总」「经营日账」「商品销量」「资金对账」四张表，冻结表头并带自动筛选。
日期最多 366 行，销量最多 100 行。金额和百分比写成精确十进制文本，汇总由服务端计算，避免
Excel 浮点格式丢失金额精度；商品名称始终作为字符串，不将 `=HYPERLINK(...)` 等名称解释成公式。

JSON 与 XLSX 使用同一统计快照及规则，工作簿记录投影代际和修订号。文件压缩发生在查询事务之外。
只允许管理员导出，返回标准 spreadsheetml MIME、附件文件名和 Cache-Control: no-store。
依赖 Apache POI 5.5.1，WebSocket starter 使用 Spring Boot 4.1.1 BOM，已完成真实运行验证。

## 投影初始化与原子重建

首次启动通过各业务模块的公开 API 补齐 P1—P5 已有事实，不要求历史上已经发布 P6 事件。成功后
initialized=true；初始化失败保留未就绪状态，工作台和报表返回 503 REPORTING_NOT_READY，
不会伪装成零数据。`REPORTING_BOOTSTRAP_ENABLED` 默认 true，失败可自动或由管理员重试。

重建事务使用 REPEATABLE_READ，超时上限 180 秒。它锁定统计控制行，在同一个事务内清空并
分批重建投影，每批最多二百条，按数据库 UUID 顺序进行键集分页，每批 flush/clear 控制 ORM 内存。
只读查询通过 MVCC 继续看到旧的完整代际；任何中途失败都回滚删除和新数据，旧版本、旧代际保留。

正常事件更新也获取同一控制锁，因此重建期间的新事件保留在可靠登记中，待重建提交后继续应用。
订单按源版本去重，较旧事件不能覆盖新快照；收款、退款和注册事实按稳定标识去重。源事件纳秒时间
统一到 PostgreSQL 微秒舍入规则，避免重放与数据库重建产生虚假的不可变事实冲突。

通知历史和阅读进度不参与统计重建。升级时不会为历史已付款订单批量制造新来单提醒，旧待处理订单
仍可从工作台和订单列表找到。

## 迁移、错误与验证

新增 V7：增加订单催单字段；创建五张 notification 表和七张 reporting 表及必要索引。
仅 notification_attempt → notification_notice、reporting_line → 本模块 order/product 有外键。
V1—V6 不修改，不清空已有订单或其他业务数据；Hibernate 仍只校验结构。

JSON 错误沿用 RFC 9457、code、traceId。NOTIFICATION_INVALID_INPUT 为 400、NOT_FOUND 为 404、
VERSION_CONFLICT／CONFLICT 为 409、INVALID_CREDENTIALS 为 401；报表 INVALID_INPUT 为 400、
VERSION_CONFLICT／CONFLICT 为 409、NOT_READY／UNAVAILABLE 为 503。员工权限错误沿用 identity。
WebSocket 升级失败按 HTTP 401/403/503 拒绝；撤销或非法消息关闭码为 1008。

验证覆盖真实 PostgreSQL/Redis/RustFS、真实 JDK HTTP/WebSocket 握手和消息、单次票据重放／过期、
Origin 限制、退出／禁用撤销、催单归属／版本／冷却、提交顺序游标、个人阅读隔离、投递耗尽／重投、
UTC 与经营日边界、精确金额、退款跨日、事件重复／乱序、XLSX 公式文本隔离、财务权限、自动初始化、
分页重建、失败原子回滚、并发读者及重建期间新事件追平。测试资源均使用独立随机 schema 与前缀。

官方技术依据：[Spring WebSocket 服务端](https://docs.spring.io/spring-framework/reference/web/websocket/server.html)、
[Apache POI XSSF 指南](https://poi.apache.org/components/spreadsheet/quick-guide.html)。

## 2026-09-19 验收记录

- `./mvnw spotless:apply` 与 `./scripts/verify.sh` 均通过：144 项测试，零失败、零错误、零跳过。
- Checkstyle 零违规，Modulith／ArchUnit 依赖约束通过，真实 PostgreSQL／Redis／RustFS 回归通过。
- P6 新增 25 项验证：8 项领域测试、16 项通知／统计／真实 WebSocket 集成测试，以及 1 项首次启动旧数据补齐测试。
- 真实连接验证一次性票据、消息接收、Origin 拒绝、过期／重放拒绝、退出及停用后的连接撤销。
- 投影验证包含 205 条源订单的跨批次重建、失败回滚、MVCC 旧代际读取和重建期间新事件追平。
- 一天和一年报表的查询次数一致且不超过 18，仅加载授权及控制元数据实体，没有逐日查询或加载订单明细后计算。
- XLSX 通过真实 POI 读取校验四张工作表、金额与 JSON 一致，以及公式样式商品名称仍为普通字符串。
- V7 仅在隔离测试 schema 中迁移和校验；没有对开发库执行迁移或清理，也未创建 P7 客户端工程。
