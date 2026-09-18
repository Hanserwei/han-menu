# P3：Flutter 顾客身份、地址簿与购物车

## 范围与架构

P3 提供后端顾客注册/登录、退出、密码修改、本人资料、地址簿和购物车。Flutter 工程、短信平台、
订单及支付宝沙箱接入属于后续阶段，本阶段没有伪造登录、支付或下单成功接口。

顾客账号由 customer 模块拥有，使用独立的 BCrypt 摘要、会话表、Redis 限流前缀及安全链。
员工继续使用 identity 的 `hme_` 令牌，顾客使用 `hmc_`，两者不可互换。cart 仅依赖
`customer :: api` 的身份授权和 `catalog :: api` 的可售商品查询，数据库无跨模块外键。

顾客身份与地址表通过 ORM 存取，地址簿变更锁定当前顾客账号行。购物车使用独立聚合和 JPA
版本锁，首次创建通过唯一主键和事务保证不会并发生成两个购物车。

## 注册与登录约定

采用**手机号标识 + 密码**，无需微信或短信供应商。手机号输入允许可选 `+` 和 7—15 位数字，
去除首尾空白后补齐 `+` 作为存储形式，不自动推断国家区号；例如 `13800138000` 与
`+13800138000` 为同一标识。需要区号的客户端应显式提交完整号码。

这只是当前个人学习项目的登录标识约定，**没有短信验证，不代表号码归属已验证**。当前也不提供
手机号变更或忘记密码找回，不能通过输入手机号直接重置密码。

密码为 8—64 个字符，UTF-8 不超过 72 字节，BCrypt 工作因子为 12。注册和登录分开：注册成功
后客户端再创建会话，不出现账号已创建但隐式登录失败时不明确的响应。

会话有效期默认 24 小时，可配置为不超过 30 天；没有自动刷新令牌。所有保护接口使用
`Authorization: Bearer <accessToken>`，不读取认证 Cookie。只持久化 SHA-256 令牌摘要。

注册与登录共用顾客独立限流：每标识每分钟默认 10 次，每直连来源默认 50 次。Redis 键不包含
号码原文，超限返回 429/Retry-After，Redis 失败返回 503，不绕过限流。当前不信任外部
X-Forwarded-For，反向代理的受信任来源配置需在部署阶段单独处理。

## 17 个新增 HTTP 操作

| 方法与路径 | 权限与用途 | 成功响应 |
| --- | --- | --- |
| POST `/api/v1/customer/accounts` | 公开，注册 phone/displayName/password | 201 顾客资料 |
| POST `/api/v1/customer/sessions` | 公开，phone/password 登录 | 201 accessToken/tokenType/expiresAt，附 Location |
| DELETE `/api/v1/customer/sessions/current` | 顾客，退出当前会话 | 204 |
| GET `/api/v1/customer/me` | 顾客，读取本人资料 | 200 id/phone/displayName/version |
| PUT `/api/v1/customer/me` | 顾客，displayName/version | 200 更新后的资料和版本 |
| PUT `/api/v1/customer/me/password` | 顾客，currentPassword/newPassword | 204，全部旧会话失效 |
| GET `/api/v1/customer/addresses` | 顾客，读取本人全部地址 | 200 数组 |
| GET `/api/v1/customer/addresses/{id}` | 顾客，读取本人单个地址 | 200 地址 DTO |
| POST `/api/v1/customer/addresses` | 顾客，创建地址 | 201 地址 DTO，附 Location |
| PUT `/api/v1/customer/addresses/{id}` | 顾客，address/version | 200 地址 DTO |
| PATCH `/api/v1/customer/addresses/{id}/default` | 顾客，version | 200 当前默认地址 DTO |
| DELETE `/api/v1/customer/addresses/{id}?version=...` | 顾客，删除本人地址 | 204 |
| GET `/api/v1/cart` | 顾客，读取本人购物车 | 200 购物车快照 |
| POST `/api/v1/cart/items` | 顾客，productId/quantity/selections/version | 200 合并后的购物车 |
| PATCH `/api/v1/cart/items/{id}` | 顾客，quantity/version | 200 更新后的购物车 |
| DELETE `/api/v1/cart/items/{id}?version=...` | 顾客，删除本人条目 | 200 更新后的购物车 |
| DELETE `/api/v1/cart/items?version=...` | 顾客，清空购物车 | 200 空购物车及当前版本 |

员工令牌访问顾客路径、顾客令牌访问员工/后台路径返回 401，不将错误种类的令牌映射为另一类
主体。匿名或过期令牌也返回 401。公开菜单和门店读取仍可匿名访问，Flutter 即使自动携带顾客
令牌也不会被当作员工登录失败。

所有写接口禁止客户端提交 customerId、角色、密码摘要、商品名称或价格等未定义字段。
ID 为 UUID 字符串，时间为 UTC ISO-8601，修改版本允许零但必须显式提供。

## 地址簿规则

每位顾客最多 20 条地址。地址字段为 label、recipientName、phone、province、city、district、
detail、defaultAddress。地址 POST 示例：

```json
{
  "label": "家",
  "recipientName": "张先生",
  "phone": "+8613800138000",
  "province": "浙江省",
  "city": "杭州市",
  "district": "西湖区",
  "detail": "示例路 1 号",
  "defaultAddress": true
}
```

PUT 使用 `{ "address": {上述完整地址字段}, "version": 0 }`。PATCH 默认地址只提交 version。

- 归属从认证身份获取，每次读写都带当前顾客 ID；他人地址与不存在地址统一返回 404。
- 初始允许没有默认地址。创建/修改时 defaultAddress=true，或调用默认地址 PATCH，原子清除其他默认地址。
- 修改时 defaultAddress=false 会显式取消该地址的默认状态；删除默认地址后也不自动选择另一条。
- 先对当前顾客账号加事务锁，再查询、校验、清除旧默认并写入新默认。数据库部分唯一索引保证最多一个默认。
- 被自动取消默认的地址也递增其 JPA 版本，因此旧页面不能在用户切换默认后静默覆盖该状态。
- 默认切换和新地址创建在同一事务内，失败或回滚不会丢失原默认地址。
- 所有地址响应只面向其所属顾客，默认字符串表示不输出收货人、电话或详细地址。

## 购物车规则与返回模型

同一顾客只有一个购物车。GET 空购物车不创建数据库记录，返回 version=0；第一次 POST 必须
携带 version=0，成功后持久化版本为 1，重试旧版本返回 409，避免第一次添加被重复执行。
已有购物车的所有变更也要求当前版本。清空后保留聚合及版本，不重新变为零。

```json
{
  "productId": "商品UUID",
  "quantity": 2,
  "selections": {"辣度": "微辣"},
  "version": 0
}
```

POST 表示增加数量，PATCH 表示替换为目标数量。同商品、同规格映射按值相等合并，规格键顺序
不影响合并；不同规格保留独立条目。每条数量 1—99，最多 50 个不同规格条目。零数量请使用删除。

菜品必选口味必须提供，未知组和不存在的选项都会拒绝；套餐的口味在其组成中固定，selections
必须为空。商品/规格失效后仍可删除，不能继续添加或改变数量。

购物车响应：

```json
{
  "items": [],
  "version": 0,
  "updatedAt": "2026-09-18T00:00:00Z",
  "estimatedTotal": 0,
  "currency": "CNY"
}
```

每条包含 id、productId、kind、name、unitPrice、quantity、selections、available、
unavailableReason、subtotal。查询时通过 catalog 读取实时可售状态及价格，同商品多规格在一次
请求内复用查询结果，展示总价只累计可售且规格有效的条目。

- 商品已下架/删除：条目保留旧展示快照，available=false，原因为 PRODUCT_UNAVAILABLE。
- 商品仍可售但旧规格失效：available=false，原因为 SELECTION_UNAVAILABLE。
- 当前可售商品重新定价：展示采用实时价格，不使用 App 输入或过去的价格。
- estimatedTotal 是**展示估算**，不是订单或付款金额；P4 下单必须再检查门店、商品、地址、版本并计价。
- 并发首次添加只有一个创建事务成功；并发后续修改由聚合版本和 Hibernate `@Version` 检查阻止丢失更新。

## 错误与安全边界

沿用 RFC 9457、code、traceId 与 X-Request-ID。顾客业务分类以 CUSTOMER_ 开头，购物车以
CART_ 开头。404 表示本人资源不存在；409 表示重复注册、数量/容量业务冲突或版本冲突；参数
错误为 400；限流 429；依赖故障 503。数据库唯一冲突及 ORM 版本冲突沿用通用 DATA_CONFLICT/
VERSION_CONFLICT。规格或商品校验也可能返回 catalog 公开用例的 CATALOG_ 错误。

顾客密码更换或账号停用会提升安全版本，即使重新启用也不会恢复旧令牌。当前提供本人改密，
停用能力在领域模型与仓储中可验证，但没有新增管理端顾客停用接口。不存在密码找回或支付自动授权。
本阶段没有新增顾客业务审计查询接口；日志和 DTO 字符串保证不包含凭证与个人资料。

## 迁移与验证

新增 V4 创建 customer_account、customer_session、customer_address、cart、cart_item。
不修改已执行的 V1—V3，不清空已有 P1/P2 数据。地址与账号在 customer 模块内部有外键；购物车
只保存顾客和商品 UUID，不跨模块建立数据库外键。

完整门禁包含领域测试、真实 PostgreSQL/Redis/RustFS 回归以及顾客/员工身份隔离、地址越权、
默认地址唯一性和回滚、并发购物车首次创建/更新、金额与规格伪造、实时价格变化、依赖故障及日志脱敏。
测试只使用随机 schema、测试 Redis 前缀和测试对象存储前缀。

2026-09-18 验收：`./scripts/verify.sh` 通过，73 项测试成功，Checkstyle 零违规。
打包后的应用已在开发库完成 V4 迁移并保留现有员工账号；真实 HTTP 验证覆盖顾客注册、登录、
身份隔离、地址增删、购物车加购/旧版本重试/清空及退出失效。OpenAPI 共 47 个操作，
其中 P3 新增 17 个；联调创建的临时顾客与商品已清理。
