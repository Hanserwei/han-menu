# customer —— 顾客账号与地址簿

代码位置：`src/main/java/com/hanserwei/hanmenu/customer/`

## 职责与边界

- 管理顾客账号（手机号 + 密码）、收货地址簿、顾客会话与注册/登录限流。
- 顾客认证不依赖微信 openid/code，密码即凭证；移动端为 Flutter App。
- 与员工身份链完全独立；购物车等模块只通过 `api` 包校验顾客身份。

## 聚合根

### CustomerAccount

顾客账号聚合，封装档案、启停用和会话撤销版本。

| 业务方法 | 说明 |
| --- | --- |
| `create(id, phone, displayName, passwordHash, now)` | 注册（手机号唯一）。 |
| `restore(...)` | 从持久化重建聚合。 |
| `reviseProfile(displayName, now)` | 修改显示名。 |
| `changePassword(hash, now)` | 修改密码，递增 `securityVersion`。 |
| `changeEnabled(bool, now)` | 管理员启停用顾客。 |
| `requireActive(securityVersion)` | 校验启用状态与会话安全版本。 |
| `requireVersion(expected)` | 乐观锁校验。 |

### DeliveryAddress（实体，归属顾客）

| 业务方法 | 说明 |
| --- | --- |
| `revise(label, recipientName, phone, 省/市/区/detail, now)` | 修改地址资料。 |
| `makeDefault(now)` / `clearDefault(now)` | 维护默认地址（聚合内互斥）。 |
| `requireVersion(expected)` | 乐观锁校验。 |

不变量：地址必须归属一个顾客；`AddressRepository` 的所有查询必须带顾客归属，
防止越权读取他人地址。

## 值对象

| 值对象 | 约束 |
| --- | --- |
| `CustomerPassword(value)` | 顾客密码值对象。 |
| `CustomerSearch` | 管理员查询条件：手机号精确匹配，名称按字面包含；时间区间为注册时刻。 |
| `CustomerPage` | 有界查询结果，不泄露 ORM 分页类型。 |

## 端口

| 端口 | 职责 |
| --- | --- |
| `CustomerRepository` | 顾客聚合仓储。 |
| `AddressRepository` | 地址仓储；查询强制顾客归属。 |
| `CustomerSessionRepository` | 顾客会话；原始令牌不进入数据库。 |
| `CustomerPasswordHasher` | 密码摘要端口，BCrypt 适配器不进入领域。 |
| `CustomerAttemptLimiter` | 注册/登录限流；依赖故障时**拒绝**创建身份或会话。 |

## 领域异常

`CustomerException.Reason`：
`INVALID_INPUT`、`INVALID_CREDENTIALS`、`NOT_FOUND`、`CONFLICT`、`VERSION_CONFLICT`、
`RATE_LIMITED`、`UNAVAILABLE`（基础设施不可用）

异常消息不包含 SQL、凭证或请求原文。

## 公开契约与集成事件

`api` 包：

- `CustomerIdentity` —— 顾客认证快照（customerId、phone、displayName、securityVersion）。
- `CustomerAuthorization` —— 供购物车等模块校验顾客身份，**不信任调用方构造的身份快照**。
- `CustomerCheckout` —— 订单结算所需的顾客与地址契约；
  `AddressSnapshot` 为交付给订单的不可变收货资料，字符串表示隐藏个人信息。
- `CustomerFacts` —— 顾客增长统计最小快照（`Registration`：仅 id 与 createdAt）。

`events` 包：

- `CustomerRegistered(customerId, createdAt)` —— 注册统计事实，不含手机号或姓名。

## 关键业务规则

1. 手机号唯一标识顾客；注册与登录共享限流端口，故障即拒绝。
2. 修改密码/停用递增 `securityVersion`，旧顾客会话失效。
3. 地址查询必须带顾客归属；默认地址在聚合内互斥维护。
4. 交付订单的是地址快照（含源版本），与地址簿后续修改/删除隔离。

## 相关文档

- [P4_CONTRACT.md](../P4_CONTRACT.md)（顾客注册、地址簿接口契约）
- [P7_BACKEND_CONTRACT.md](../P7_BACKEND_CONTRACT.md)（顾客管理仅限当前管理员）
- 数据库：`V4__customer_and_cart.sql`
