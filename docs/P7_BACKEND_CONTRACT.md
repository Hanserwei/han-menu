# P7 前置：PC 管理端后端能力补齐

日期：2026-09-19。依据 PC 页面规划逐项核对 P1—P6 真实控制器与安全链，补齐四类管理端能力。
本轮只涉及后端、测试与契约；PC 技术路线仍为 Vue 3 + TypeScript + antdv-next，尚未创建客户端工程。
Flutter 工具链准备按用户指令暂缓。

## 页面—权限—接口核对

| 页面／能力 | 权限 | 真实接口及核对结论 |
| --- | --- | --- |
| 员工登录、本人资料、改密、退出 | 员工 | sessions、me 已完整提供 |
| 员工管理 | ADMIN | employees 增查改、启停用已提供；角色为固定 ADMIN/STAFF |
| 分类、菜品、口味、套餐、图片 | ADMIN | catalog 已提供；菜品与套餐统一 product 资源，口味随商品读写 |
| 门店资料与营业状态 | ADMIN | shop 已提供 |
| 工作台 | ADMIN / STAFF | workspace 已提供，普通员工响应不包含财务金额 |
| 订单查询与履约 | ADMIN / STAFF | management/orders 详情与动作已提供；本轮补订单 UUID、顾客 UUID、收货电话及创建时间组合检索 |
| 来单、催单、阅读进度 | ADMIN / STAFF | notifications 补查、票据与 WebSocket 已提供 |
| 通知轨迹与重投 | ADMIN | notifications/{id}/attempts、redelivery 已提供 |
| 经营分析、销量、对账、导出 | ADMIN | reports 已提供；财务口径遵循 P6 |
| 统计投影查看与重建 | ADMIN | reports/projection 已提供 |
| 顾客档案与启停用 | ADMIN | 本轮新增 management/customers 列表、详情及 status |
| 支付／退款流水 | ADMIN | 本轮新增 management/payments、management/refunds 列表与详情 |
| 身份安全审计检索 | ADMIN | 本轮新增 management/audit-events；范围明确为已登记的安全事件 |

上述管理端首版能力均有对应后端接口。独立营销、动态角色权限、多店、骑手分配、任意金额退款、
管理端代改顾客密码／地址、全业务操作审计不在当前业务契约中，不能据此自动生成页面或接口。

## 公共查询约定

- 本文路径都在 `/api/v1` 下，均使用员工 Bearer；新增资源要求当前有效 ADMIN，订单作业仍允许 STAFF。
- 顾客令牌不能访问管理端，员工令牌不能访问顾客支付接口；应用层再次检查真实角色及安全版本。
- `page` 从 0 开始，默认 0，范围 0—10000；`size` 默认 20，范围 1—50。
- 返回 `items/page/size/totalElements/totalPages`；无匹配返回空集合，越过末页仍返回正确匹配总量。
- 可选筛选采用 AND 组合，全部在数据库查询；不能在当前页集合中过滤后充当全量筛选。
- `from/to` 为带时区的 ISO-8601 时刻，推荐 UTC `Z`，语义为 **from ≤ 时间 < to**。
  两者可以单独提供，同时提供时必须 from < to。订单、顾客、支付、退款按各自 `createdAt`，审计按 `occurredAt`。
  页面选择上海经营日期时，将日期起点和结束日期的次日零点转成 UTC 后查询；不要把此约定混同 P6 报表的包含首尾日期。
- 按时间降序、UUID 降序稳定排序；退款按自身创建时刻，不按支付创建或退款确认时刻。
- 非法参数、未知状态、空的电话／名称筛选和非法区间返回 400；详情不存在返回 404；旧版本返回 409。
  失败沿用 RFC 9457，包括 `code/traceId`；新资源继续使用 Spring Security 默认禁止缓存的响应策略。

## 订单组合检索

`GET /api/v1/management/orders`

| 参数 | 语义 |
| --- | --- |
| status | UNPAID、PAID、ACCEPTED、DELIVERING、COMPLETED、CANCELLING、REFUNDING、CANCELLED |
| orderId | 订单 UUID 精确匹配；当前 UUID 就是订单唯一编号，不新增另一个显示订单号 |
| customerId | 下单顾客 UUID 精确匹配 |
| phone | 完整收货手机号精确匹配，允许输入省略前导加号的形式，服务端规范化 |
| from/to | 订单创建时刻的左闭右开区间 |
| page/size | 零基分页 |

电话来自成交时的不可变地址快照，不查询当前顾客登录手机号或地址簿。URL 中的加号应编码为 `%2B`。
原摘要响应不变，不额外返回个人资料；需要履约资料时仍通过详情读取。付款、接单、拒绝、取消、
配送、完成及退款状态机沿用 P4/P5/P6，没有新增任意状态修改入口。

## 管理员顾客管理

| 方法与路径 | 输入／用途 |
| --- | --- |
| GET `/api/v1/management/customers` | phone/name/enabled/from/to/page/size |
| GET `/api/v1/management/customers/{id}` | 指定 UUID 的顾客档案 |
| PATCH `/api/v1/management/customers/{id}/status` | `{"enabled":false,"version":0}`，200 返回最新档案与版本 |

`phone` 按顾客账号规范精确匹配，`name` 为去掉两端空白后的字面包含查询，1—50 个字符。
`%`、`_` 和转义符不会被当作 SQL 通配符；`enabled` 为布尔值。from/to 按注册创建时刻筛选。

档案返回 id、phone、displayName、enabled、version、createdAt、updatedAt。
不返回 passwordHash、securityVersion、令牌、地址簿或购物车；前端从顾客详情跳到订单列表时传 customerId。

启停用在顾客账号行锁内验证版本；和现有下单／地址操作共用同一账号锁。停用推进 securityVersion，
令旧会话失效；重新启用继续推进安全版本，不恢复旧令牌。目标状态与当前相同且版本正确时返回当前档案，
不重复变更或记录审计。陈旧版本即便目标状态相同仍拒绝，避免绕过并发约束。

真实状态变更通过 identity 的公开 StaffAudit 契约登记 CHANGE_CUSTOMER_STATUS，仅记录管理员 UUID、
顾客 UUID 和成功结果。状态及审计在同一事务提交；任何失败或回滚都不会留下半成功记录。
停用不删除历史订单，不中断已付款订单的商家履约或服务端支付处理。

## 管理员支付与退款查询

| 方法与路径 | 输入／用途 |
| --- | --- |
| GET `/api/v1/management/payments` | status/orderId/customerId/paymentId/from/to/page/size |
| GET `/api/v1/management/payments/{id}` | 支付单详情 |
| GET `/api/v1/management/refunds` | status/orderId/customerId/paymentId/from/to/page/size |
| GET `/api/v1/management/refunds/{id}` | 退款单详情 |

支付 status 为 PENDING、SUCCEEDED、CLOSED；退款 status 为 PENDING、SUCCEEDED。
`orderId` 对应 payment 模块持有的业务引用，`paymentId` 在支付列表按自身标识、在退款列表按原支付标识筛选。
payment 不依赖 ordering 类型，不查询订单表，也不通过跨模块关联补充顾客个人资料。

支付返回 id、orderId、customerId、amount、currency=CNY、status、tradeNo、createdAt、expiresAt、
paidAt、closeRequested、nextAttemptAt、lastFailure、version。
退款返回 id、paymentId、orderId、customerId、amount、currency=CNY、status、tradeNo、createdAt、
confirmedAt、nextAttemptAt、lastFailure、version。

列表和详情都来自服务端持久化事实。GET 不触发渠道查单、不登记退款、不修改状态；后台工作器继续
负责重试及确认。lastFailure 是已有的固定失败分类，不回传渠道异常正文。响应不含幂等键、请求指纹、
签名参数、私钥或凭证。历史付款／退款在对应状态真正确认后才显示成功，统计汇总仍使用 P6 的独立报表口径。

## 身份安全审计查询

`GET /api/v1/management/audit-events?action=LOGIN&actorId=...&subjectId=...&successful=true&from=...&to=...`

支持 action、actorId、subjectId、successful、from、to、page、size。action 为现有安全事件枚举：
BOOTSTRAP、LOGIN、LOGOUT、CREATE_EMPLOYEE、UPDATE_EMPLOYEE、CHANGE_STATUS、CHANGE_PASSWORD、
AUTHORIZATION_DENIED、LOGIN_LIMITED，以及本轮新增 CHANGE_CUSTOMER_STATUS。

每条仅返回 id、action、actorId、subjectId、successful、occurredAt；主体可能为空，不附加个人资料。
此接口展示身份模块实际登记的安全事实，不宣称已记录所有商品、订单或门店变更；不提供历史审计修改／删除能力。

## 持久化、边界与验收

新增 V8 Flyway 迁移只增加管理端查询索引，不改写旧迁移、不清空数据、不建立跨模块外键。
索引覆盖稳定时间排序、订单收货电话、顾客启用状态、交易状态／顾客／业务引用，以及审计动作／主体。
UUID 与唯一电话查询沿用已有主键或唯一索引。名称包含查询在当前数据量下不引入新扩展索引。

组合查询使用本模块 JPA Specification。订单列表保持摘要投影，不抓取明细集合分页；应用层执行授权、
事务和 DTO 映射。customer 与 payment 新增 identity::api 依赖，identity 不反向依赖它们，领域层仍只依赖 Java。

ManagementCapabilitiesIt 使用独立真实 PostgreSQL schema，验证：

- ADMIN/STAFF/顾客/匿名权限，顾客与员工安全链隔离，应用层伪造角色及陈旧身份拒绝。
- 订单历史收货电话、AND 组合、分页总量、时间左闭右开边界及无匹配行为。
- 顾客名称字面匹配、状态过滤、敏感字段排除、停用／启用后的会话撤销。
- 同版本并发启停用只有一个成功者、状态与审计共同回滚、无变化不重复审计。
- 支付／退款组合筛选、未确认退款状态、无渠道调用、响应不含支付凭证。
- 审计条件及稳定分页、非法参数／资源不存在的错误格式、OpenAPI 新路由与参数可发现。

质量门禁为 `./mvnw spotless:apply` 与 `./scripts/verify.sh`，保留全部架构和真实中间件集成测试。

2026-09-19 验证结果：spotless 已应用，完整 verify 成功；Checkstyle 0 违规，52 项单元／架构测试与
103 项集成测试全部通过，无失败、错误或跳过。新增 11 项集成测试包含管理端和顾客端 OpenAPI 模型隔离断言。
