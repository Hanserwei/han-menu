# P1：身份与员工接口

## 当前实现范围

本阶段提供员工账号、登录/退出、员工管理、自己的密码修改、管理员权限边界、结构化审计与接口文档。
身份模块独立拥有 `identity_employee`、`identity_session`、`identity_audit` 三张表，迁移版本为 V4。
其他业务模块保留骨架，尚未实现的业务路由默认拒绝访问。

P1 提供一个初始化管理员 `ADMIN` 和普通员工 `STAFF` 两种固定角色。管理员由部署初始化流程创建，
不能通过员工接口停用、删除或重新授权；所有新增员工固定为 STAFF。多管理员授权、动态角色和菜单
权限不是本阶段功能，不能通过提交额外 role 字段开启。

## 启动与初始管理员

```bash
# 更新/补齐本地中间件及随机初始凭证，不重置已经存在的账号密码。
python3 scripts/middleware.py up

# 启动应用；默认监听 127.0.0.1:8080。
./scripts/with-env.sh ./mvnw spring-boot:run
```

初始用户名位于 `.env` 的 `IDENTITY_BOOTSTRAP_USERNAME`，默认 `admin`；初始密码位于
`IDENTITY_BOOTSTRAP_PASSWORD`，由本地初始化脚本随机生成，不是原教程固定密码。
密码不会打印到启动日志。管理员已存在时，重启不会重置密码；即使管理员改名，也不会新建另一个管理员。
完成初始化后可设置 `IDENTITY_BOOTSTRAP_ENABLED=false` 关闭启动初始化。

运行配置缺少有效初始密码且启用了初始化时，启动会失败，不使用默认弱密码兜底。
员工自行修改密码后，`.env` 中原先的初始密码不会随之更新，也不再是登录密码。

启动后访问 [Swagger UI](http://127.0.0.1:8080/swagger-ui/index.html)，或读取
[OpenAPI JSON](http://127.0.0.1:8080/v3/api-docs)。文档公开，业务操作仍需各自的身份权限。
可以在 Swagger 的登录接口登录，再将返回 token 填入 Authorize 的一种认证方式中。

## 认证契约

保留原服务端路径 `/admin/employee/*`。原后台浏览器 `/api/*` 经过 Nginx 转发到 `/admin/*`，
本次不更改该映射。原客户端把登录 token 当作字符串保存、读取并放入请求头，不依赖 JWT 声明解析。

支持二选一，不能同时携带，也不能重复同名认证头：

```http
token: hme_登录接口返回的令牌
```

```http
Authorization: Bearer hme_登录接口返回的令牌
```

令牌为 256 位随机值的 URL-safe Base64 编码，带 `hme_` 员工身份前缀；默认有效期八小时。
这是不透明会话令牌，不是 JWT。数据库只保存 SHA-256 摘要和过期时间，不保存原始 token。
每次请求查询当前账号状态及安全版本，不依赖进程缓存或 Redis TTL 撤销身份。

服务端不通过 Cookie 认证，不创建 JSESSIONID，不开启 Basic/Form 登录。原前端可以继续在自己的
Cookie 中保存 token，但必须由脚本显式复制到请求头。跨来源站点不能依赖自动携带的 Cookie 获得权限。
登录接口忽略陈旧 token 请求头，保证旧客户端仍可重新登录；其他接口严格验证令牌。

## 响应、分页、时间与标识

成功继续使用旧信封：

```json
{"code":1,"msg":null,"data":{"id":1,"userName":"admin","name":"系统管理员","token":"示例占位值","role":"ADMIN","expiresAt":"2026-09-18T00:00:00Z"}}
```

员工接口失败使用真实 HTTP 错误状态，同时保留旧信封：

```json
{"code":0,"msg":"用户名或密码错误","data":null}
```

| HTTP 状态 | 含义 |
| --- | --- |
| 400 | 参数校验失败、重复/混用认证头等 |
| 401 | 缺少身份、登录失败、会话过期/撤销、修改密码时旧密码不正确 |
| 403 | 普通员工访问管理员能力，或访问尚未开放的业务路由 |
| 404 | 管理员查询的员工不存在 |
| 405 / 415 | 请求方法或内容类型不受支持 |
| 409 | 用户名重复、乐观锁冲突或试图停用管理员 |
| 429 | 超过登录频率，附带 Retry-After 秒数 |
| 500 / 503 | 内部错误或数据库/登录限流基础设施不可用，不返回底层异常详情 |

密码错误、未知账号、已停用账号在登录失败时都使用统一的凭证错误消息。
不能依靠响应文案判断账号是否存在。非 `/admin/*` 边界的安全错误使用 Problem Details。

分页为 `data: {"total":数量,"records":数组}`，`page` 从 1 开始、最大 10000，`pageSize` 为
1—100，默认 10。姓名过滤按大小写不敏感的字面子串处理，`%`、`_` 不作为 SQL 通配符。

为兼容原后台，员工 ID 使用 JSON 整数。内部数据库主键为 bigint，当前客户端按常规 JavaScript
数字处理；进入可能超出安全整数范围的数据规模前需要版本化升级 ID 契约，不能无声改变序列化。
员工 `createTime`、`updateTime` 为 `Asia/Shanghai` 时区的 `yyyy-MM-dd HH:mm` 字符串，
保留原项目定制序列化格式；新增 `expiresAt` 使用 ISO-8601 UTC 时间。
响应带 `X-Request-ID`；请求追踪标识由服务端生成，不直接信任外部传入的日志内容。

## 已实现的 10 个 HTTP 操作

| 方法与路径 | 权限 | 输入与行为 |
| --- | --- | --- |
| POST `/admin/employee/login` | 公开 | username、password；返回 id、userName、name、token，追加 role、expiresAt |
| POST `/admin/employee/logout` | 已登录员工 | 撤销当前令牌；该账号其他有效会话保留 |
| GET `/admin/employee/me` | 已登录员工 | 返回自己的 id、userName、name、role，不暴露内部会话字段 |
| POST `/admin/employee` | ADMIN | 创建 STAFF；username、name 必填；phone、sex、idNumber、password 可选 |
| GET `/admin/employee/page` | ADMIN | page、pageSize、name；返回 total、records |
| GET `/admin/employee/{id}` | ADMIN | 读取员工资料，永不返回 password/passwordHash |
| PUT `/admin/employee` | ADMIN | id、username、name 及资料字段；可附 version；不能修改角色、状态或密码 |
| GET `/admin/employee/status/{status}?id=...` | ADMIN | 保留旧后台调用，status 只能为 0/1；响应标记 Deprecation 并禁止缓存 |
| PATCH `/admin/employee/{id}/status` | ADMIN | 推荐写接口；JSON 包含 status，可附 version |
| PUT `/admin/employee/password` | 已登录员工 | oldPassword、newPassword；仅修改本人，成功后该账号全部旧会话失效 |

旧 GET 启停用入口是明确保留的兼容适配，内部调用与 PATCH 相同的用例，不另写一套业务逻辑。
等待原前端可以维护并修改请求方式时，再安排移除该入口，不设置未经确认的下线日期。

用户名规范化为小写，要求字母开头、3—32 位字母数字或下划线。姓名 1—50 字符。
电话和身份证允许为空，非空时校验格式；身份证格式校验不表示真实身份核验。
sex 使用 `0`、`1`、`2`，分别为女、男、未指定，省略默认为 2。

新增员工未提交 password 时，服务端生成独立随机密码，并在本次响应的 `data.initialPassword`
中返回一次。提交有效密码时该字段为 null。原后台页面不会自动展示这个新增字段，创建账号时应
使用 Swagger/API 指定密码，或在创建响应中接收生成值；不会恢复原教程的统一 `123456`。

密码要求 12—64 字符、UTF-8 不超过 72 字节，存储为 BCrypt 工作因子 12 的自适应摘要。
资料响应追加 version 字段：新客户端应在编辑时回传以检测陈旧页面；旧客户端未传时仍保留仓储
层的并发更新保护，但不能检测用户打开页面后长时间未提交产生的陈旧编辑。
JSON 中额外的 role/status/password 等资料编辑字段不会参与聚合映射，不能通过批量属性赋值越权。

## 登录限流、会话与审计

- Redis 独立按账号和直连客户端地址计数：默认每账号每分钟 10 次，每来源每分钟 50 次。
- 两道计数通过同一个 Lua 脚本原子执行并设置 TTL，键使用摘要，不包含用户名或 IP 原文。
- 不直接信任 X-Forwarded-For。接入受信任反向代理后，应另行配置并验证真实客户端地址解析。
- Redis 连接失败时登录返回 503；已经创建的会话由 PostgreSQL 验证，不因 Redis 故障自动失效或放行。
- 停用和密码变更递增安全版本。再次启用不会让停用前的 token 恢复有效。
- 每次成功登录清理过期会话。到期行尚未清理时也无法通过认证。
- 审计表记录初始化、登录成功/失败/限流、退出、员工变更及权限拒绝。字段为固定动作、结果、内部
  操作者/对象编号和发生时间；不记录密码、token、姓名、手机号、身份证或原始请求体。
- 成功业务变更及审计在同一个数据库事务中提交。失败登录的审计单独按当前用例的失败事务约定保留。
  控制台诊断日志是动作记录，已提交的数据库审计记录才是业务结果依据。

## 技术选型与验证

- Spring Security 7.1.1、Spring Data Redis 等跟随 Boot 4.1.1 BOM。
- springdoc-openapi 3.1.1 提供公开 OpenAPI 和 Swagger UI。
- Guava 33.7.1-jre 用于 SHA-256 摘要及 Base64 编码；随机性仍由 SecureRandom 提供。
- Commons Lang3 3.20.0 跟随 Boot BOM，用于边界字符串规范化。领域模型保持纯 Java。
- `IdentityModuleIt` 使用 `@ApplicationModuleTest`，真实 PostgreSQL schema 和 Redis 测试键前缀隔离。
- 覆盖退出/停用/改密失效、普通员工越权、资料批量赋值、过期/伪造/重复令牌、Redis 限流、来源伪造、
  数据库故障拒绝访问、审计日志脱敏、旧响应格式和 OpenAPI。
- CI 已增加固定版本 Redis 服务；自动化测试使用其隔离命名空间，不访问 PVE 服务。

接口契约由自动化测试验证；原始后台构建产物的完整交互联调仍按 P7 执行。本阶段没有实现微信登录、
商品、下单或支付业务。
