# identity —— 员工身份与权限

代码位置：`src/main/java/com/hanserwei/hanmenu/identity/`

## 职责与边界

- 管理员工账号、固定角色权限、登录会话与安全审计。
- 员工会话以数据库状态为准（`SessionRepository`），Redis 登录限流不可用时**拒绝登录**，不降级放行。
- 不负责顾客身份（见 [customer.md](customer.md)）；两者会话与安全链完全独立。

## 聚合根：EmployeeAccount

员工账号聚合，通过业务方法维护启停用、资料和凭证状态，不暴露任意 setter。

| 业务方法 | 说明 |
| --- | --- |
| `create(id, profile, hash, role, now)` | 创建账号（管理员用例调用）。 |
| `restore(...)` | 从持久化重建聚合（含 `securityVersion`/`version`）。 |
| `reviseProfile(profile, now)` | 修改资料（用户名/显示名/手机号）。 |
| `changeEnabled(bool, now)` | 启用/停用账号。 |
| `changePassword(hash, now)` | 修改密码凭证。 |
| `requireActive(securityVersion)` | 校验账号启用且会话安全版本未失效。 |
| `requireAdministrator()` | 校验管理员角色。 |
| `requireVersion(expected)` | 乐观锁校验（修改资源必须带 version）。 |

关键状态：

- `securityVersion`：会话撤销版本，改密/停用后递增，使已发会话失效。
- `version`：聚合乐观锁版本。
- `Role` 枚举：当前阶段为 `ADMIN`（管理员）/ `STAFF`（员工），后续可按真实权限模型演化。

## 值对象

| 值对象 | 约束 |
| --- | --- |
| `EmployeeProfile(username, displayName, phone)` | 构造时统一规范化并校验；`normalizeUsername` 提供规范化规则。 |
| `NewPassword(value)` | 仅在设置密码时短暂使用，拒绝超出 BCrypt 有效输入范围的密码。 |

## 端口

| 端口 | 职责 |
| --- | --- |
| `EmployeeRepository` | 员工聚合仓储；领域层不依赖 ORM 类型。 |
| `SessionRepository` | 保存令牌摘要和撤销版本；**原始令牌不能进入数据库**。 |
| `PasswordHasher` | 密码摘要端口，算法由基础设施提供，领域不接触 Spring Security。 |
| `LoginAttemptLimiter` | 登录频率控制；不可用时必须拒绝登录。 |
| `AuditTrail` / `AuditQuery` | 结构化最小安全审计的登记与查询端口。 |

## 领域异常

`IdentityException.Reason`：
`INVALID_CREDENTIALS`、`FORBIDDEN`、`NOT_FOUND`、`CONFLICT`、`VERSION_CONFLICT`、
`INVALID_INPUT`、`RATE_LIMITED`、`UNAVAILABLE`（基础设施不可用）

`AuditTrail.Action`（固定审计事件类型）：
`BOOTSTRAP`、`LOGIN`、`LOGOUT`、`CREATE_EMPLOYEE`、`UPDATE_EMPLOYEE`、`CHANGE_STATUS`、
`CHANGE_CUSTOMER_STATUS`、`CHANGE_PASSWORD`、`AUTHORIZATION_DENIED`、`LOGIN_LIMITED`

审计事实（`AuditQuery.Entry`）只含内部标识、动作、结果与时刻，**不记录请求体、认证头、
凭证或自由文本**，防止注入与凭证泄露。

## 公开契约与集成事件

`api` 包（`@NamedInterface`）：

- `StaffIdentity` —— 认证后的员工身份快照（employeeId、username、role、securityVersion）。
- `StaffAuthorization` —— 供其他模块做员工权限检查，无需读取身份模块内部类型。
- `StaffAudit` —— 固定安全事件登记入口。
- `StaffSessions` —— 长连接会话证明（`Proof` 只含会话摘要，不外发原始令牌）。

`events` 包：当前无集成事件。

## 关键业务规则

1. 员工会话以数据库状态为准；限流依赖不可用时登录失败，不降级。
2. 停用账号或修改密码后，`securityVersion` 递增，旧会话全部失效。
3. 审计只登记固定事件类型与内部标识；凭证类型必须提供脱敏字符串表示。
4. 修改员工资料必须带 `version`，版本不匹配抛 `VERSION_CONFLICT`。

## 相关文档

- [P1_CONTRACT.md](../P1_CONTRACT.md)（身份与员工接口契约）
- [P7_BACKEND_CONTRACT.md](../P7_BACKEND_CONTRACT.md)（安全审计查询仅限当前管理员）
- 数据库：`V1__identity.sql`
