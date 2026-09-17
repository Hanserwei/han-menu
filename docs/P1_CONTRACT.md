# P1：身份与员工资源契约

## 运行与认证

```bash
python3 scripts/middleware.py up
./scripts/with-env.sh ./mvnw spring-boot:run
```

初始管理员默认为 `.env` 中的 `IDENTITY_BOOTSTRAP_USERNAME`，密码为同文件的
`IDENTITY_BOOTSTRAP_PASSWORD`，由部署脚本随机生成。已有本项目管理员不会因重启而被重置。
用户自行改密后，环境中的初始化密码不再有效；初始化可通过配置关闭。

启动后访问 [Swagger UI](http://127.0.0.1:8080/swagger-ui/index.html)，机器可读契约为
[OpenAPI](http://127.0.0.1:8080/v3/api-docs)。所有接口均为本系统新设计的资源协议。

创建会话返回 `accessToken`、`tokenType`、`expiresAt`。后续认证仅使用：

```http
Authorization: Bearer hme_会话接口返回的随机值
```

令牌是 256 位随机值，不是 JWT。原始值只在创建时返回，数据库保存 SHA-256 摘要。
默认有效期八小时。服务端不从 Cookie 或其他自定义请求头读取身份。

## 9 个 HTTP 操作

| 方法与路径 | 权限 | 成功结果 |
| --- | --- | --- |
| POST `/api/v1/sessions` | 公开，受登录限流保护 | 201；会话 DTO；Location 指向当前会话 |
| DELETE `/api/v1/sessions/current` | 当前员工 | 204；当前令牌撤销 |
| GET `/api/v1/me` | 当前员工 | 200；id、username、displayName、role |
| PUT `/api/v1/me/password` | 当前员工 | 204；更换密码并撤销该账号全部旧会话 |
| POST `/api/v1/employees` | ADMIN | 201；员工 DTO；Location 指向新资源 |
| GET `/api/v1/employees` | ADMIN | 200；分页查询结果 |
| GET `/api/v1/employees/{id}` | ADMIN | 200；员工 DTO |
| PUT `/api/v1/employees/{id}` | ADMIN | 204；按版本更新资料 |
| PATCH `/api/v1/employees/{id}/status` | ADMIN | 204；按版本变更状态 |

新增员工 JSON 包含 username、displayName、password，可选 phone。初始密码必须显式提供，
不生成对调用方不可见的默认密码。角色由服务端固定为 STAFF，不能在请求中自行提升。

更新资料包含 username、displayName、version，可选 phone；资源 id 来自路径。
修改状态包含 `status: "ACTIVE"` 或 `"DISABLED"`，并必须提供 version。
修改本人密码包含 currentPassword、newPassword，不接受其他员工的 id。

用户名为字母开头的 3—32 位字母、数字或下划线，规范化为小写；显示名称 1—50 字符。
phone 可为空，非空时为可选 + 号和 7—15 位数字。密码为 12—64 字符、UTF-8 不超过 72 字节。
所有未知 JSON 字段被拒绝，缺失版本号或未定义状态返回 400。

员工 DTO 包含 UUID 字符串 id、username、displayName、phone、role、status、version、
createdAt、updatedAt。时间统一为 ISO-8601 UTC。不会返回密码摘要、ORM 实体或内部安全版本。

## 分页与并发

`GET /api/v1/employees?page=0&size=20&name=王`：page 从零开始、最大 10000，size 为 1—100。
姓名过滤是大小写不敏感的字面子串；`%`、`_` 由 Spring Data 转义，不作为通配符。
按 createdAt 降序、id 升序稳定排序。

```json
{"items":[],"page":0,"size":20,"totalElements":0,"totalPages":0}
```

变更请求必须带读取到的 version。领域模型先拒绝陈旧编辑，Hibernate `@Version` 再防止事务
提交时的并发覆盖；冲突返回 409 和 VERSION_CONFLICT，调用方重新获取资源后决定如何处理。

## 错误协议

错误使用 `application/problem+json`，HTTP 状态本身表达失败。

```json
{
  "type":"urn:han-menu:problem:version-conflict",
  "title":"Conflict",
  "status":409,
  "detail":"资源已被修改，请刷新后重试",
  "instance":"/api/v1/employees/示例UUID",
  "code":"VERSION_CONFLICT",
  "traceId":"请求追踪UUID"
}
```

| HTTP | 典型 code | 语义 |
| --- | --- | --- |
| 400 | INVALID_REQUEST / INVALID_INPUT | 字段、状态、版本、认证头格式不合法 |
| 401 | UNAUTHENTICATED / INVALID_CREDENTIALS | 缺少认证、错误密码、过期或撤销的令牌 |
| 403 | FORBIDDEN | 权限不足或能力尚未开放 |
| 404 | NOT_FOUND | 员工资源不存在 |
| 405 / 415 | METHOD_NOT_ALLOWED / UNSUPPORTED_MEDIA_TYPE | 请求方法或内容类型不合法 |
| 409 | VERSION_CONFLICT / DATA_CONFLICT / CONFLICT | 并发写入、唯一约束或业务状态冲突 |
| 429 | RATE_LIMITED | 超过频率限制，附 Retry-After |
| 500 / 503 | INTERNAL_ERROR / UNAVAILABLE | 内部故障或依赖服务不可用 |

响应不包含异常堆栈、SQL、拒绝的字段原值或凭证。traceId 同时出现在 X-Request-ID 响应头。

## 会话、权限与审计

- ADMIN 维护员工，STAFF 访问本人身份、改密与退出。P1 通过初始化建立一个受保护管理员。
- 停用和修改密码会递增安全版本；再次启用不恢复停用前令牌。
- 每次认证通过 JPA 实体图读取当前账号，单次查询完成会话状态验证。
- Redis 每账号每分钟默认 10 次、每直连地址每分钟 50 次；不盲目信任 X-Forwarded-For。
- Redis 故障拒绝创建会话；已有会话由 PostgreSQL 认证，不由缓存是否命中决定权限。
- 密码存储为 BCrypt 工作因子 12 的摘要。审计仅保存固定事件类型、成功标志、内部 UUID 和时刻。
- 审计使用 ORM 并参与用例事务，诊断日志不记录姓名、电话、密码或令牌。

## 验证范围

实际 PostgreSQL、Redis 及 Hibernate 测试覆盖认证、撤销、权限、未知字段拒绝、版本冲突、
分页、依赖故障和日志脱敏。另验证两个并发 EntityManager 的版本锁，以及聚合和 Modulith JPA
事件登记同事务回滚。接口文档由当前控制器生成，不以参考项目 API 作为兼容测试基线。
