# Han Menu · 全新外卖系统

参考苍穹外卖的业务能力，使用 **Java 25 + Spring Boot 4.1.1 + Spring Modulith 2.1.1**
构建面向单店外卖业务的 DDD 模块化单体。一个可执行 JAR 承载九个业务模块，PC 管理端使用
**Vue 3 + TypeScript + antdv-next**，覆盖资料管理、订单履约、实时提醒和经营分析。

**当前进度：后端 P1—P6、管理端 PC-1—PC-6 已实现。** 顾客身份、地址、购物车、订单与支付
API 已提供；顾客移动端选定 Flutter，工程和支付 SDK 真机联调尚待推进。支付测试使用
**支付宝沙箱**，范围见 [移动端与支付决策](docs/MOBILE_PAYMENT_DECISION.md)。

快速导航：[项目能力](#项目能力) · [架构总览](#架构总览) · [关键业务流程](#关键业务流程) ·
[本机启动](#本机启动) · [工程结构](#工程结构) · [开发与验证](#开发与验证) · [文档导航](#设计与使用)

## 项目能力

| 业务范围 | 已实现能力 | 使用入口 |
| --- | --- | --- |
| 身份与权限 | 员工与顾客独立会话、管理员／员工授权、改密撤销、登录限流与安全审计 | 管理端 + 顾客 API |
| 商品与门店 | 分类、菜品、口味、套餐、私有图片、上下架、营业状态与公开目录缓存 | 管理端 + 顾客 API |
| 顾客与购物车 | 顾客档案、地址簿、默认地址、版本化购物车与选项校验 | 管理端 + 顾客 API |
| 订单与履约 | 幂等下单、成交快照、历史与再来一单、接单／拒单／配送／完成 | 管理端 + 顾客 API |
| 支付与退款 | 沙箱 App 参数、通知验签、查单、关单、全额退款与故障恢复 | 支付 API + 管理端查询 |
| 通知与统计 | 来单／催单、一次性票据长连接、HTTP 补查、阅读恢复、经营投影与 XLSX 导出 | 管理端 |

## 架构总览

### 运行与部署

![Han Menu 本机容器运行架构：管理端经 Nginx 访问模块化单体，后端连接 PostgreSQL、Redis、RustFS 和支付宝沙箱](docs/diagrams/system-architecture/system-architecture.svg)

图示独立本机五容器实例：Nginx 提供静态管理端、API／WebSocket 和签名图片代理；后端通过
JPA 持久化业务数据及事件登记，Redis 承担登录限流与公开目录缓存，RustFS 保存私有商品图片。
生产部署使用 HTTPS／WSS；本机体验实例入口为 `127.0.0.1:18080`。

[交互图](docs/diagrams/system-architecture/system-architecture.html) ·
[绘图源文件](docs/diagrams/system-architecture/candidate.json)

### 模块与可靠事件

![Han Menu 核心模块协作：订单调用公开结算与支付 API，持久化事件驱动订单、通知和报表](docs/diagrams/module-collaboration/module-collaboration.svg)

结算公开契约汇总 `customer / shop / catalog / cart` 四个模块，`ordering` 在一个短本地事务
内完成校验、快照、购物车结算与事件登记。图示订单主链路，通用授权和统计重建的读取依赖
见 [架构决策](docs/ARCHITECTURE.md)。

`ordering` 调用 `payment` 的公开 API，支付通过 `PaymentResult / RefundResult` 事件返回结果；
`payment` 不反向依赖订单实现。Spring Modulith 使用 PostgreSQL 的 `event_publication` 表登记
事件，消费者独立事务、幂等处理，未完成消费可恢复重投。

[交互图](docs/diagrams/module-collaboration/module-collaboration.html) ·
[绘图源文件](docs/diagrams/module-collaboration/candidate.json)

## 关键业务流程

### 幂等下单与支付重试

同一顾客使用相同 `Idempotency-Key` 和请求摘要重试时返回原订单。新下单由服务端校验地址、
营业状态、购物车版本和当前商品规格／价格，保存独立成交快照；订单、所选购物车项移除和
`OrderChanged` 登记共同提交。支付参数生成是后续用例，意图提交后签发，同键重试保留原支付单。

<details>
<summary>展开下单流程图：幂等分支、结算事务与支付重试</summary>

![顾客幂等下单流程，包含同键重放、实时结算校验、原子提交和原支付单重试](docs/diagrams/order-checkout/order-checkout.svg)

</details>

[交互图](docs/diagrams/order-checkout/order-checkout.html) ·
[绘图源文件](docs/diagrams/order-checkout/candidate.json) · [下单契约](docs/P4_CONTRACT.md)

### 订单状态与补偿退款

![订单生命周期：待支付、待接单、已接单、配送、完成，以及取消、关单、退款和迟到付款补偿](docs/diagrams/order-lifecycle/order-lifecycle.svg)

订单主流程为 `UNPAID → PAID → ACCEPTED → DELIVERING → COMPLETED`。绑定支付意图后仍为
`UNPAID`；确认付款首次进入 `PAID` 才发布来单事件。支付成功以服务端验签通知或主动查单为准。

有支付单的取消会等待关单或全额退款确认，结果未知时保留 `CANCELLING / REFUNDING`。
已取消订单遇到迟到付款会补偿退款，始终保持取消状态。订单状态、支付状态和退款状态分别建模。

[交互图](docs/diagrams/order-lifecycle/order-lifecycle.html) ·
[绘图源文件](docs/diagrams/order-lifecycle/candidate.json) · [支付与履约契约](docs/P5_CONTRACT.md)

### 实时通知与阅读恢复

管理端验证当前员工身份后，并行开始 HTTP 同步和取票连接；收到有效 `READY` 才标记实时可用。
WebSocket 帧触发 HTTP 补查，由完整连续页面推进拉取游标。本人已读进度使用独立版本，员工
明确确认当前页后才提交；断线仍可补查，版本冲突或写入结果未知时先重读。

<details>
<summary>展开通知时序图：一次性票据、HTTP 补查与显式已读</summary>

![管理端通知恢复时序：并行启动、一次性票据握手、实时帧触发补查和版本化已读确认](docs/diagrams/notification-recovery/notification-recovery.svg)

</details>

[交互图](docs/diagrams/notification-recovery/notification-recovery.html) ·
[绘图源文件](docs/diagrams/notification-recovery/candidate.json) · [通知前端契约](docs/PC4_CONTRACT.md)

上述图表使用 [Archify v3.1.0](https://github.com/tt-a1i/archify/tree/v3.1.0) 绘制，源码证据固定到
提交 `95431d1`；SVG 可直接在 GitHub 查看。GitHub 文件页展示 HTML 源码，下载交互图后用浏览器
打开即可探索节点和路径。更新方式见 [图表维护说明](docs/diagrams/README.md)。

## 本机启动

### 开发环境

准备 JDK 25、Python 3、Podman／podman-compose、Node `24.21.0`、pnpm `12.4.2`。
Maven Wrapper 固定为 `3.9.16`，无需单独安装 Maven。完整浏览器与生产验收还需要 Chromium、
OpenSSL，以及 Podman 或 Docker。

```bash
# 仓库根：初始化开发中间件、数据库、私有桶和本地随机凭证。
python3 scripts/middleware.py up
./scripts/install-hooks.sh
pnpm --dir admin install --frozen-lockfile

# 首次准备浏览器验收；Linux 需安装相应系统依赖。
pnpm --dir admin exec playwright install --with-deps chromium
```

分别在两个终端启动后端与管理端：

```bash
# 终端一：后端监听 127.0.0.1:8080，允许实际开发管理端 Origin。
./scripts/with-env.sh env \
  NOTIFICATION_ALLOWED_ORIGINS=http://127.0.0.1:5173 \
  ./mvnw spring-boot:run

# 终端二：Vite 同时代理 HTTP API 和 WebSocket。
pnpm --dir admin dev
```

管理端入口为 <http://127.0.0.1:5173>。开发代理默认指向 `127.0.0.1:8080`，可通过
`ADMIN_API_TARGET` 修改；使用 `localhost` 或其他来源时，应同步配置精确的通知 Origin。
需要让开发中间件随登录启动时，可执行 `python3 scripts/middleware.py install-service`。

应用环境变量在被 Git 忽略的 `.env`。初始管理员用户名默认 `admin`，密码由
`IDENTITY_BOOTSTRAP_PASSWORD` 提供，首次启动自动创建，重启不重置。IDE 启动需配置相同环境变量。

启动后可访问 [Swagger UI](http://127.0.0.1:8080/swagger-ui/index.html)、
[OpenAPI JSON](http://127.0.0.1:8080/v3/api-docs) 和健康检查 `/actuator/health`。
身份认证仅使用 Bearer 请求头。资源统一位于 `/api/v1`，错误采用 RFC 9457。

### 独立本机体验实例

完成 [完整验证](#开发与验证) 得到后端 JAR 和前端 `dist/` 后，可构建并启动独立五容器实例：

```bash
python3 scripts/deploy-local.py deploy
python3 scripts/deploy-local.py install-service
python3 scripts/deploy-local.py status
```

入口为 <http://127.0.0.1:18080>，默认数据目录为 `~/.local/share/han-menu`；首次随机管理员密码
保存在该目录的 `ACCESS.md`。停止或更新容器保留业务数据。启停、冷备份与沙箱配置见
[本机容器部署](docs/LOCAL_CONTAINER_DEPLOYMENT.md)，公网 HTTPS／WSS 部署见
[管理端部署手册](admin/DEPLOYMENT.md)。

## 工程结构

```text
han-menu/
├── src/main/java/       后端九个业务模块与应用装配
├── src/main/resources/  应用配置、Flyway 迁移
├── src/test/            架构、领域与真实中间件集成测试
├── admin/               Vue 管理端、单元／浏览器测试与 Nginx 模板
├── infra/               开发中间件与独立本机实例编排
├── scripts/             验证、环境加载、中间件管理和部署工具
└── docs/                API 契约、架构决策与 Archify 图表
```

后端按限界上下文组织：

```text
com.hanserwei.hanmenu
├── HanMenuApplication
├── identity       员工身份与账号（已实现）
├── customer       顾客身份与地址（已实现）
├── shop           门店经营（已实现）
├── catalog        分类、菜品、口味、套餐（已实现）
├── cart           购物车（已实现）
├── ordering       订单、支付协作与履约（已实现）
├── payment        支付宝沙箱支付、全额退款与结果事件（已实现）
├── notification   可靠通知、员工长连接与阅读进度（已实现）
└── reporting      工作台、经营日账、销量与导出（已实现）
```

各模块使用 domain/application/infrastructure/web 分层，api/events 为明确发布的模块契约。
identity、catalog、shop、customer、cart、ordering、payment、notification、reporting 的持久化集中在 `infrastructure/persistence`：Spring Data 接口、JPA 实体及仓储适配器。
领域模型封装业务行为，HTTP 仅返回专用 DTO。

## 关键基线

- Spring Data JPA / Hibernate（由 Spring Boot BOM 管理）：ORM 映射、派生查询、版本锁、实体图。
- Flyway：V1 身份模型，V2 Modulith JPA 事件登记，V3 目录与门店，V4 顾客与购物车，V5 未支付订单，V6 支付与履约，V7 通知和经营投影，V8 管理端检索索引；Hibernate 只校验结构，Open-in-View 关闭。
- UUID 业务标识、UTC 时刻、必填更新版本、零基分页、严格 JSON 字段校验。
- Spring Security、BCrypt、持久化会话、Redis 双维度登录限流、最小化安全审计。
- Guava 33.7.1-jre、Commons Lang3 3.20.0、springdoc-openapi 3.1.1。
- PostgreSQL 18.6、Redis 8.10.1、RustFS 1.0.0，统一 Compose/systemd 管理。
- Vue 3.5.43、TypeScript 5.9.3、Vite 8.3.0、antdv-next 1.5.4；Pinia 管理会话与通知状态，TanStack Vue Query 管理业务查询缓存，ECharts 展示经营图表。

开发数据库 `han_menu` 与测试数据库 `han_menu_test` 独立使用，首次只初始化管理员，业务资料由
管理端录入。顾客启停用、组合订单检索、资金流水和安全审计查询见
[管理端前置接口契约](docs/P7_BACKEND_CONTRACT.md)。

## 开发与验证

```bash
# Java 代码改动后的格式化。
./mvnw spotless:apply

# 统一执行前后端完整提交门禁。
./scripts/verify.sh
```

统一门禁包含 Google Java Format／Checkstyle、ArchUnit／Modulith 模块边界、真实
PostgreSQL／Redis／RustFS 集成测试，以及前端格式、类型、模块边界、OpenAPI 一致性、单元测试、
生产构建、开发浏览器和 Nginx HTTPS／WSS 生产浏览器验收。GitHub Actions 在 push／PR 时执行
同一入口，配置见 [Verify 工作流](.github/workflows/verify.yml)。

提交钩子和 CI 使用完整验证流程。Google Checkstyle 警告也阻止提交，测试源码同样参与检查。
新克隆需安装钩子；建议将 CI 的 Verify 检查设为分支保护必需检查。

中文 Javadoc 摘要使用英文句点结束以满足 Google 原始检查规则，正文使用中文标点。
领域规则、事务约束、异常和资源生命周期需要准确说明；不通过注释重复代码表面行为。

报告位于 `target/checkstyle-result.xml`、`target/surefire-reports/`、`target/failsafe-reports/`。
模块文档和图位于 `target/spring-modulith-docs/`。
前端浏览器报告位于 `admin/test-results/`；更多命令见 [管理端开发说明](admin/README.md)。

集成测试使用随机 PostgreSQL schema 与 Redis 测试前缀，完成后清理。ORM 测试额外验证并发
EntityManager 的乐观锁、单查询认证，以及业务更新和持久化事件登记的原子回滚。

## PC 管理端

独立 [admin/ 工程](admin/README.md) 使用 `app / modules / shared` 分包；路由按业务懒加载。
服务端 `/me` 验证当前员工身份和角色，前端菜单与守卫提供对应入口，后端再次检查授权。

| 访问角色 | 管理端页面 |
| --- | --- |
| ADMIN / STAFF | 工作台、订单检索与履约、通知中心、本人改密与退出 |
| ADMIN | 员工、顾客、分类／菜品／套餐／图片、门店营业管理 |
| ADMIN | 支付／退款流水、对账、经营报表、销量与 XLSX 导出 |
| ADMIN | 安全审计、通知投递诊断与重投、统计投影重建 |

交付细节见 [PC-1 基础](docs/PC1_CONTRACT.md)、[PC-2 资料管理](docs/PC2_CONTRACT.md)、
[PC-3 订单作业](docs/PC3_CONTRACT.md)、[PC-4 实时通知](docs/PC4_CONTRACT.md)、
[PC-5 经营与维护](docs/PC5_CONTRACT.md)、[PC-6 部署验收](docs/PC6_CONTRACT.md)。

## 设计与使用

- [架构决策](docs/ARCHITECTURE.md)：模块边界、面向对象、JPA、事务与接口设计。
- [领域模型](docs/domain-model/README.md)：九个限界上下文的聚合、值对象、端口与业务不变量。
- [Archify 图表](docs/diagrams/README.md)：静态预览、交互文件、源码证据与维护方式。
- [P1 验收](docs/P1_ACCEPTANCE.md)：进入 P2 前的范围与验证结果。
- [P2 API](docs/P2_CONTRACT.md)：分类、菜品、口味、套餐、图片、缓存和门店营业。
- [P6 API](docs/P6_CONTRACT.md)：催单、可靠通知、WebSocket、经营统计、XLSX 导出与投影重建。
- [P5 API](docs/P5_CONTRACT.md)：支付宝沙箱、验签通知、全额退款、恢复任务及员工履约。
- [P4 API](docs/P4_CONTRACT.md)：幂等下单、快照、历史、取消与再来一单。
- [P3 API](docs/P3_CONTRACT.md)：Flutter 顾客登录、地址默认切换和版本化购物车。
- [P1 API](docs/P1_CONTRACT.md)：新会话与员工资源、认证、错误、分页与版本约定。
- [实施计划](docs/REFACTORING_PLAN.md)：九个业务模块及后续阶段。
- [业务参考清单](docs/REFERENCE_CAPABILITIES.md)：从苍穹外卖识别的功能需求。
- [中间件管理](docs/INFRASTRUCTURE.md)：本机 PostgreSQL、Redis、RustFS 的统一管理。
- [开发约定](AGENTS.md)：中文注释、Google Java 规范和提交门禁。
