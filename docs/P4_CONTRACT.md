# P4：完整未支付订单生命周期

## 范围与模块边界

P4 实现订单提交、详情、分页历史、未付款取消和再来一单到购物车。订单状态为 `UNPAID` 与
`CANCELLED`，唯一状态迁移为 `UNPAID → CANCELLED`。不提供任意改状态、模拟支付成功、接单、
履约或 Flutter 工程。支付有效期、自动超时取消以及查单／关单竞争随 P5 真实支付契约一并实现；
本阶段未取消的订单保持待付款，不虚构渠道交易或过期支付结果。

ordering 只依赖 `customer :: api`、`shop :: api`、`catalog :: api` 与 `cart :: api`。
领域只依赖 Java 和 ordering 自己的领域类型；应用服务编排短事务，订单聚合维护金额和取消规则。
DTO、领域快照和 JPA 实体分别建模，转换集中完成。跨模块只保存 UUID，无跨模块外键或表查询。

## 五个顾客 HTTP 操作

使用独立顾客安全链和 `Authorization: Bearer hmc_...`。员工令牌、匿名、失效会话返回 401。
请求禁止未定义字段，因此 customerId、total、unitPrice、status、收货资料不能由客户端注入。

| 方法与路径 | 请求 | 成功响应 |
| --- | --- | --- |
| POST `/api/v1/orders` | `Idempotency-Key` 请求头；addressId/addressVersion/cartVersion/itemIds | 新建 201；幂等重放 200；均返回详情、Location 和 Idempotency-Replayed |
| GET `/api/v1/orders/{id}` | 本人订单 UUID | 200 订单详情 |
| GET `/api/v1/orders?page=0&size=20` | page 0—10000；size 1—50 | 200 items/page/size/totalElements/totalPages |
| POST `/api/v1/orders/{id}/cancellation` | `{ "version": 0 }` | 200 已取消订单详情及新版本 |
| POST `/api/v1/orders/{id}/reorder` | `{ "version": 1, "cartVersion": 2 }` | 200 `{ "cartVersion": 3 }`；随后 GET `/api/v1/cart` |

下单示例，所有版本均必须显式提供，零也是有效版本：

```http
POST /api/v1/orders
Authorization: Bearer <顾客令牌>
Idempotency-Key: checkout-20260919-001
Content-Type: application/json
```

```json
{
  "addressId": "f07ef1eb-5bf6-49ef-8478-0db7dc5c6327",
  "addressVersion": 0,
  "cartVersion": 3,
  "itemIds": ["202ff47f-8e7e-4924-9c79-5216d14c7b04"]
}
```

itemIds 必须包含 1—50 个不同的本人购物车条目标识。每个所选条目按当前完整数量结算，不支持
同一条目拆分数量。选择部分条目时，未选条目即使已下架也会保留，不阻止其他有效条目下单。
空购物车、空选择、重复条目和不存在／他人条目不能建单。

详情字段为 id、status、version、createdAt、cancelledAt、address、items、total、currency。
所有时刻为 UTC ISO-8601，所有金额为精确十进制人民币元，currency 固定 CNY。

- address：sourceId/sourceVersion、recipientName、phone、province、city、district、detail。
- items：id、productId、kind、name、unitPrice、quantity、selections、components、subtotal。
- components：套餐当时组成的 productId、name、quantity、selections；菜品为空列表。
- total：各条目 unitPrice × quantity 的精确求和。本阶段无配送费、包装费、优惠或库存预占。
- 历史按 createdAt 降序、id 降序稳定排序，摘要仅包含 id/status/total/currency/version/createdAt/cancelledAt。
  不读取明细或收货地址，不使用集合抓取分页，不在 HTTP 序列化阶段延迟查询。

## 服务端重验与事务隔离

一次新建订单事务按以下顺序执行：

1. customer 锁定顾客账号行并重验当前安全版本和启用状态。同一顾客的地址变更、订单提交、取消和
   再来一单串行执行；没有记录的首次幂等键也可安全并发，不依赖进程内互斥。
2. 查找本顾客的幂等键，已成功提交则验证请求摘要并返回原订单，跳过地址、门店、目录和购物车重验。
3. 通过 customer API 校验地址归属及 addressVersion，复制当前收货资料。账号锁保持到提交，
   地址不会在复制期间被修改或删除。
4. 通过 shop API 对门店行加共享悲观锁并确认 OPEN；并发关店写入等待事务结束。
5. 通过 cart API 校验 cartVersion 与所选条目归属，读取商品标识、数量、规格。
6. 通过 catalog API 持有目录修订行锁，直接从数据库验证商品／分类可售状态及必选口味，
   套餐还验证全部组成菜品及其固定口味。成交名称、单价和套餐组成均来自当前目录，不读展示缓存。
   该只读结算锁不会递增目录修订号，也不会制造新缓存代际。
7. 保存订单及全部明细快照，再通过 cart API 移除所选条目，递增并返回购物车持久化版本。
   订单、提交幂等记录和购物车结算共用本地事务，任何异常或提交失败全部回滚。

目录锁按单店短事务串行化结算与目录编辑；事务中没有 Redis、对象存储或支付网络调用。购物车
仍允许独立编辑，Hibernate `@Version` 检查会使发生并发修改的结算事务回滚，保留已提交的新条目
或新数量。即使发生在“读取选择”与“保存订单”之间，也不会误清空购物车或留下半成品订单。
客户端收到版本冲突后应重读购物车，再用新版本和新幂等键表达新的下单意图。

## 幂等规则

Idempotency-Key 为 1—128 位 ASCII 字母、数字或 `. _ : -`，作用域为顾客的订单提交用例。
数据库唯一约束为 `(customer_id, idempotency_key)`。请求摘要使用 SHA-256，固定字段边界并
按 UUID 排序 itemIds；JSON 属性顺序、itemIds 顺序不影响同请求判断。

同键同 addressId/addressVersion/cartVersion/itemIds 返回同一订单标识和它的**当前状态**。
同键不同内容返回 409 `ORDER_IDEMPOTENCY_CONFLICT`。并发相同请求只有一次 201，另一次为
200 重放；不同键抢同一购物车版本只能一次成功。失败并回滚的请求不占用键，可以原键重试。

幂等结果不依赖当前购物车：后来加购、门店关店、商品下架／删除、地址修改／删除，均不影响重放。
订单取消后仍保留键，原提交请求重放已取消订单，不能复用旧键重新建单。幂等记录随订单保留，
没有自动过期清理。响应不返回幂等键或请求摘要。

## 取消与再来一单

取消要求本人订单及当前订单版本，由聚合确认状态仍为 UNPAID。成功递增版本、保存 cancelledAt；
金额、条目与地址完全不变。旧版本返回 409 `ORDER_VERSION_CONFLICT`，以最新版本重复取消返回
409 `ORDER_STATE_CONFLICT`。取消不恢复购物车；顾客可显式再来一单。

UNPAID 或 CANCELLED 订单均可再来一单，只要求订单和购物车版本最新。该操作原子合并全部条目
到现有购物车，按商品与规格合并数量，保留其他购物车条目，复用每条 1—99、最多 50 条的限制。
当前价格和可售规格重新从目录取得，历史成交价不复制为新价格。任一条目失效、套餐不可售、
规格失效、合并后超限或并发版本冲突时，整个操作失败，不部分加购、不静默跳过失效商品。

再来一单不要求门店正在营业、不复制旧地址、不创建新订单、不修改原订单状态。顾客读取购物车并
选择当前地址后，显式提交新的订单。重试相同旧 cartVersion 会返回 409，避免网络重试重复加量。

## 错误与个人资料

RFC 9457 响应包含 code、traceId，并附 X-Request-ID。ORDER_INVALID_INPUT 为 400；
ORDER_NOT_FOUND 为 404；ORDER_SHOP_CLOSED、ORDER_VERSION_CONFLICT、ORDER_STATE_CONFLICT、
ORDER_IDEMPOTENCY_CONFLICT 为 409。校验涉及的其他模块保留 CUSTOMER_、CART_、CATALOG_ 分类；
ORM 并发失败沿用 VERSION_CONFLICT，数据库唯一约束沿用 DATA_CONFLICT。

他人订单与不存在订单均返回相同 404；所有详情、取消、再来一单及历史查询限制归属。收货地址
在领域、公开契约和响应 DTO 的字符串表示中脱敏，不向事件、审计或日志写入个人资料。
P4 没有新增异步消费者或无消费者的演示事件，未来真实支付／通知事件仍须与状态同事务登记。

## 迁移与验证

新增 `V5__unpaid_orders.sql`，创建 ordering_order、ordering_line；V1—V4 保持不变。
唯一键保护幂等提交，订单历史有归属／时间／标识复合索引；订单明细外键仅指向订单模块自己的表。
数据库约束保护金额、数量、版本及取消状态／时间。启动仍由 Hibernate 校验结构，Flyway 执行迁移。

自动化验证覆盖精确金额与不可变集合、快照与地址／目录删除隔离、实时调价、部分购物车结算、
并发相同键与不同键、失败事务原子回滚、并发加购使旧结算回滚、门店／地址／目录／购物车重验、
取消与重放、重新加购合并与失败原子性、越权与身份隔离、严格请求字段和有界历史查询、套餐组成。
测试运行在独立测试库的随机 schema，沿用测试 Redis 和对象存储隔离，不清空开发数据。

2026-09-19 验收：`./mvnw spotless:apply` 和 `./scripts/verify.sh` 均成功。共 92 项测试通过，
零失败、零跳过，Checkstyle 零违规，Modulith／ArchUnit 边界检查通过。P4 新增 3 项领域测试与
16 项真实数据库集成测试，包含历史标量投影的查询数量检查和 5 个订单操作的 OpenAPI 校验。
V5 已在隔离测试 schema 完成迁移与 Hibernate 校验；没有对开发库执行订单迁移或业务数据清理。
