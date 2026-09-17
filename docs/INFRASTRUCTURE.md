# 中间件与外部服务准备清单

## 当前环境

按用户最新指示，本项目的 PostgreSQL、Redis 和 RustFS 已全部纳入同一份 Compose 和 systemd
服务管理，不连接 Hannote 的 PVE 实例。
版本已于 2026-09-17 核对官方正式发布，运行容器内的版本命令也已验证。

| 服务 | 本机地址 | 版本 | 数据位置 |
| --- | --- | --- | --- |
| PostgreSQL | `127.0.0.1:5432` | 18.6，han-menu-postgres | `han-menu-postgres-data` 命名卷；保留原集群的全部数据库 |
| Redis | `127.0.0.1:6379` | 8.10.1 | `han-menu-redis-data` 命名卷，启用 AOF |
| RustFS S3 | `http://127.0.0.1:9000` | 1.0.0 正式版 | `han-menu-rustfs-data` 命名卷 |
| RustFS 控制台 | `http://127.0.0.1:9001/rustfs/console/` | 同一 RustFS 实例 | 使用 .env 中的 Access Key / Secret Key 登录 |

官方发布：[Redis 8.10.1](https://github.com/redis/redis/releases/tag/8.10.1)、
[RustFS 1.0.0](https://github.com/rustfs/rustfs/releases/tag/1.0.0)。Compose 固定具体镜像版本，
配置见 `infra/compose.yml`。Redis、RustFS 只绑定回环地址；PostgreSQL 在本次迁移中保留原有
`0.0.0.0:5432` 绑定，以兼容原访问方式。新环境默认使用 `127.0.0.1`，由
`POSTGRES_BIND_ADDRESS` 控制。当前主机的统一用户服务已启用自动启动。

### 启停与验证

```bash
# 一次性启动三项服务，并幂等准备项目数据库、账号和 Bucket。
python3 scripts/middleware.py up
python3 scripts/middleware.py install-service

# 当前已启用的用户服务负责启动和停止；停止保留命名卷及数据。
systemctl --user start han-menu-middleware.service
systemctl --user stop han-menu-middleware.service
systemctl --user restart han-menu-middleware.service

python3 scripts/middleware.py status
python3 scripts/middleware.py verify
```

本机 `Linger=yes`，因此已启用的用户单元可在系统启动后运行；其他主机如未启用 linger，则在用户
会话启动后运行。若移动仓库路径，重新运行 `install-service` 更新单元。移除部署前先停止/禁用
用户单元；普通停止不删除数据，不应使用 `down -v` 作为日常停止命令。

部署脚本使用系统 Python 标准库和 podman-compose，不新增应用 SDK。它只允许本机端点，不能
拿来初始化远端 Hannote 资源。实际 Spring 缓存与图片存储适配器仍按 P1/P2 实施。

旧 `scripts/setup-local-db.py` 保留为 `middleware.py up` 的兼容入口，不再独立启动 `pg18`。

### 原 pg18 的迁移记录与回退点

2026-09-17 完成同版本迁移，过程如下：

1. 检查原集群数据库和客户端连接，完成全库 `pg_dumpall` 逻辑备份。
2. 正常停止 `pg18` 后，将 `pg18-lab-data` 冷导出，再导入新卷 `han-menu-postgres-data`。
3. 使用相同 PostgreSQL 18.6 镜像创建 Compose 服务 `postgres`，容器为 `han-menu-postgres`。
4. 比较迁移前后全库导出；仅去掉 pg_dump 每次生成的随机限制标识后，内容完全一致。
5. 验证受限应用账号对开发库/测试库的认证和读写；验证新集群初始化与重复执行的幂等性。

保留的数据库为 `postgres`、`pg18_lab`、`han_menu`、`han_menu_test`，账号和密码不变。
原 `pg18` 容器已停止，原 `pg18-lab-data` 卷未删除。不要将旧容器与新容器同时启动到 5432 端口。

受保护备份位于 `.local/backups/pg18-20260917T142030Z/`，包含 `cluster.sql`、`volume.tar`、
校验和、迁移前配置和导出比对结果。备份目录及原卷是迁移时刻的回退点，不包含迁移后的新写入。
若需要临时回退，先停止统一 systemd 服务，再启动原 `pg18`；回退前应明确如何保留迁移后的数据。
本次未执行回退，也未清理任何旧数据卷。

### 凭证、资源与已验证范围

- 随机生成的 Redis 密码、RustFS Access Key / Secret Key 写入权限为 600 的 `.env`，未进入 Git。
- PostgreSQL 管理员密码保存在 `POSTGRES_PASSWORD`，与应用的 `DB_PASSWORD` 分离；迁移时保留原值。
- 当前是项目专用开发实例，RustFS 启动凭证同时用于控制台管理；后续应用接入时按需要拆分受限应用凭证。
- Redis 使用 `han-menu:` 前缀；RustFS 已建立私有 `han-menu`、`han-menu-test` 两个 Bucket。
- `.local/redis.conf` 含运行凭证，所在目录权限为 700，整个目录被 Git 忽略；单文件允许容器内 Redis 用户读取。
- 已验证本机映射端口上的 Redis 认证、写入、读取、删除，以及两个 S3 桶的上传、读取、删除。
- 已验证 PostgreSQL 受限角色的开发/测试库认证、建表、写入、读取与事务回滚；Java 集成测试验证宿主机映射端口。
- 已验证容器重启后 Redis 键和 S3 对象保持，验证数据随后删除；控制台页面返回 HTTP 200。
- PostgreSQL 纳管后再次统一重启三项服务，确认数据库记录、缓存键、存储对象和原凭证均保持；验证数据已清理。
- 根路径 `http://127.0.0.1:9001/` 对普通 HTTP 探测可能返回 S3 的 403，请使用上面的明确控制台路径。

## PVE 备选资源盘点

以下保留之前的远端检查结果，供将来需要时参考；其中的 beta 版本提示仅针对 PVE 部署文件，
本机已经运行正式版 1.0.0。

2026-09-17 根据用户提供的 `/home/hanserwei/.config/hannote` 检查了 PVE 内网部署。
该目录的 `dev.env` 提供连接信息，`deploy/*/compose.yml` 提供部署版本和拓扑。
已有远程 Redis 和 S3 兼容对象存储可作为接入候选，无需另建一套基础服务。
本地 PostgreSQL 18.6 现由 `han-menu-postgres` 承载，见上面的迁移记录。

### 已验证的核心服务

| 服务 | 地址 | 验证结果 | 本项目建议 |
| --- | --- | --- | --- |
| Redis | `192.168.1.112:6379` | AUTH 成功、PING 返回 PONG；INFO server 返回 7.4.7 | 复用实例；使用项目专用账号/权限和 `han-menu:` 键前缀 |
| RustFS S3 API | `http://192.168.1.112:9000` | 使用已有凭证进行签名 HeadBucket 和 ListBuckets 均返回 200 | 可作为图片存储候选；接入前处理版本选择并建立项目专用 Bucket |
| PostgreSQL | `192.168.1.112:5432` | 登录及只读元数据查询成功；运行版本 16.15 | 保持本地 18.6 为当前开发库，远端实例可供后续需要时选择 |

Redis 版本来自运行实例。RustFS 的 Compose 文件固定为 `1.0.0-beta.12`，这是部署配置版本，
本次未通过管理接口确认运行镜像版本；认证检查证明 S3 接口可访问，不代表所有读写能力已经验证。
官方已发布 [RustFS 1.0.0 正式版](https://github.com/rustfs/rustfs/releases/tag/1.0.0)。
考虑项目优先稳定版本的要求，建议先核对实际运行版本，再评估升级或使用独立稳定实例；本次没有升级服务。

远端目前有 `hannote` Bucket；没有发现 `han-menu`、`han-menu-test` Bucket，也没有
`han_menu`、`han_menu_test` 远端数据库。它们属于不同项目，不能直接复用 Hannote 的业务命名空间。
建议后续为本项目准备 `han-menu`、`han-menu-test` Bucket 和限定访问范围的应用凭证。
远端盘点没有创建资源、写入测试对象、写 Redis 键或修改 Hannote 的配置与数据。

### 其他已发现服务

以下只验证了 TCP 连接，不表示已验证认证、API 或完整业务功能：

| 服务 | 地址 | 当前是否需要 |
| --- | --- | --- |
| ScyllaDB | `192.168.1.112:9042` | 当前无使用场景 |
| Elasticsearch | `192.168.1.115:9200` | 搜索需求明确后再评估 |
| PowerJob | `192.168.1.115:7700` | 需要独立调度平台时再评估 |
| Nacos | `192.168.1.239:8848` | 当前单体骨架不依赖配置中心 |
| RocketMQ NameServer | `192.168.1.239:9876` | 需要进程外事件分发时再评估 |

配置目录还包含腾讯云 COS 的配置项，本次没有访问云服务或验证其凭证。
只选择项目需要的连接项，不整体加载 Hannote 的 `dev.env`，避免带入其数据库、认证及配置中心设置。

当前骨架只依赖 PostgreSQL，其余资源不会阻塞骨架的编译、测试和启动。

| 资源 | 何时需要 | 用途 | 请准备的信息 |
| --- | --- | --- | --- |
| PostgreSQL 18.6 | 已就绪 | 业务数据、Flyway、Modulith 事件登记 | 已在本地 `.env` 配好 |
| Redis | 本机已就绪，P1/P2 接入 | 会话失效、商品缓存、限流按用例启用 | 凭证和前缀已在 .env，后续编写应用配置与适配器 |
| 图片对象存储 | 本机已就绪，P2 接入 | RustFS 1.0.0，菜品和套餐图片 | 两个专用 Bucket 已建立，后续编写存储端口与 SDK 适配器 |
| 微信小程序账号 | P3 真实登录联调 | code 换取用户身份 | AppID、AppSecret 的本地环境变量、开发者工具权限及合法域名配置 |
| 微信支付商户资源 | P5 真实支付联调 | 下单、支付通知、退款和对账 | 可用商户号、AppID 绑定、API v3 配置、平台公钥/证书方案和公网 HTTPS 回调地址 |
| Nginx 或等价反向代理 | P7；若提前使用旧后台则提前准备 | 前端静态资源、旧 `/api/` 转发、WebSocket、TLS | 监听地址、端口、域名、证书位置及静态文件目录 |

当前 PostgreSQL、Redis、RustFS 都已准备好，无需再安排这三项基础服务。
应用接入发生在对应业务阶段，当前骨架不会因新增环境变量而自动实现缓存或上传功能。
后续仍需按阶段准备微信账号、支付商户及回调环境。

## Redis 约定

- 使用部署时核实的最新稳定版本，并固定具体镜像 tag；不使用浮动 `latest` 保持环境可复现。
- Spring 客户端优先使用 Boot BOM 管理的 Spring Data Redis/Lettuce；接入时验证协议兼容性。
- 独立键前缀和测试前缀，避免清理其他应用数据；不以 `KEYS *` 作为正常缓存失效方案。
- 门店状态和订单等业务事实持久化在 PostgreSQL，不能只保存在 Redis。
- 缓存允许回源数据库；认证相关 Redis 故障不能退化为绕过认证。
- 开发实例可监听本机 6379，并准备持久化目录；是否需要 AOF 取决于实际会话数据策略。

参考：[Redis 官方容器部署说明](https://redis.io/docs/latest/operate/oss_and_stack/install/install-stack/docker/)。

## 对象存储二选一

| 方案 | 需要的信息 | 接入方式 |
| --- | --- | --- |
| S3 兼容服务 | Endpoint、Region、Bucket、访问密钥配置位置、是否需要 path-style、图片访问地址 | 实现文件存储端口的 S3 适配器 |
| 阿里云 OSS | Endpoint、Region、Bucket、访问密钥配置位置、图片域名 | 实现同一个文件存储端口的 OSS 适配器 |

不同时引入两个 SDK。先确定服务，再选择与 JDK 25 兼容的稳定 SDK。约定开发和测试使用独立
Bucket 或对象前缀；测试只删除自己上传的对象。图片可选受限上传、公开读取的专用空间，或私有空间
加签名读取；实际读策略在 P2 确定。数据库保存对象标识和业务关联，不保存临时签名 URL。

## 支付联调的前置条件

计划保留可替换的支付网关端口；测试用假网关只用于自动化测试。没有可用商户资质或回调环境时，
可以完成领域建模、签名校验测试和契约测试，但不能将它们标记为真实支付联调完成。
当前参考源码的“直接标记支付成功”不会进入正式业务实现。

支付配置应按接入时的官方 SDK 和商户公钥/证书方案确定，不能把示例配置直接复制使用。

## 本阶段无需部署

模块间消息使用同一应用内的 Spring Modulith 及 PostgreSQL 持久化事件登记，不需要额外 Kafka
或 RabbitMQ。参考源码的超时任务可先以 Spring 调度配合数据库幂等更新实现，无需单独部署任务平台。
全文检索、分布式配置中心、独立注册中心不在当前业务需求内。

未来需要跨进程订阅或多实例推送时，再评估消息中间件；需要多实例任务抢占时，先明确租约与锁策略。
依据：[Spring Modulith 事件机制](https://docs.spring.io/spring-modulith/reference/events.html)。
