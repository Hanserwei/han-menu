# Han Menu · 全新外卖系统

参考苍穹外卖的业务能力，以 Java 25、Spring Boot 4.1.1、Spring Modulith 2.1.1 构建 DDD
模块化单体。当前已实现 P1 员工身份、P2 商品与门店，P3 顾客身份、地址簿和购物车，P4 完整未支付订单生命周期，P5 支付宝沙箱支付、退款与订单履约，以及 P6 通知、鉴权长连接和经营统计。

顾客移动端采用 **Flutter App**，支付测试采用 **支付宝沙箱**。后端按移动端需求规划接口，
当前未创建 Flutter 工程，支付后端与沙箱交易闭环已实现，边界见 [移动端与支付决策](docs/MOBILE_PAYMENT_DECISION.md)。

## 设计与使用

- [架构决策](docs/ARCHITECTURE.md)：模块边界、面向对象、JPA、事务与接口设计。
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

## 本机启动

环境：JDK 25、Python 3、Podman/podman-compose；Maven Wrapper 固定为 3.9.16。

```bash
# 初始化三项中间件、项目数据库、Bucket 和本地随机凭证。
python3 scripts/middleware.py up
python3 scripts/middleware.py install-service
./scripts/install-hooks.sh

# 格式、规范、模块架构、真实 PostgreSQL/Redis/RustFS 及 ORM 验证。
./scripts/verify.sh

# 启动应用，默认监听 127.0.0.1:8080。
./scripts/with-env.sh ./mvnw spring-boot:run
```

应用环境变量在被 Git 忽略的 `.env`。初始管理员用户名默认 `admin`，密码由
`IDENTITY_BOOTSTRAP_PASSWORD` 提供，首次启动自动创建，重启不重置。IDE 启动需配置相同环境变量。

启动后可访问 [Swagger UI](http://127.0.0.1:8080/swagger-ui/index.html)、
[OpenAPI JSON](http://127.0.0.1:8080/v3/api-docs) 和健康检查 `/actuator/health`。
身份认证仅使用 Bearer 请求头。资源统一位于 `/api/v1`，错误采用 RFC 9457。

## 工程结构

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

- Spring Data JPA 4.1.1 / Hibernate 7.4.5.Final：ORM 映射、派生查询、版本锁、实体图。
- Flyway：V1 身份模型，V2 Modulith JPA 事件登记，V3 目录与门店，V4 顾客与购物车，V5 未支付订单，V6 支付与履约，V7 通知和经营投影；Hibernate 只校验结构，Open-in-View 关闭。
- UUID 业务标识、UTC 时刻、必填更新版本、零基分页、严格 JSON 字段校验。
- Spring Security、BCrypt、持久化会话、Redis 双维度登录限流、最小化安全审计。
- Guava 33.7.1-jre、Commons Lang3 3.20.0、springdoc-openapi 3.1.1。
- PostgreSQL 18.6、Redis 8.10.1、RustFS 1.0.0，统一 Compose/systemd 管理。

参考项目仅提供业务输入。本系统没有旧账号导入、旧 API 适配层或演示表。
项目数据库为 `han_menu`、`han_menu_test`，与 `pg18_lab` 等其他数据库分开管理。
当前提供员工、顾客、地址、购物车、商品、门店、支付、订单履约、通知及经营报表接口。
管理端新增组合订单检索、顾客启停用、资金流水和安全审计查询，见 [管理端前置接口契约](docs/P7_BACKEND_CONTRACT.md)。App 真机 SDK 联调随 P7 Flutter 工程推进。

## 开发与验证

```bash
./mvnw spotless:apply
./scripts/verify.sh
```

提交钩子和 CI 使用完整验证流程。Google Checkstyle 警告也阻止提交，测试源码同样参与检查。
新克隆需安装钩子，远程仓库建立后将 CI 验证设置为分支保护必需检查。

中文 Javadoc 摘要使用英文句点结束以满足 Google 原始检查规则，正文使用中文标点。
领域规则、事务约束、异常和资源生命周期需要准确说明；不通过注释重复代码表面行为。

报告位于 `target/checkstyle-result.xml`、`target/surefire-reports/`、`target/failsafe-reports/`。
模块文档和图位于 `target/spring-modulith-docs/`。

集成测试使用随机 PostgreSQL schema 与 Redis 测试前缀，完成后清理。ORM 测试额外验证并发
EntityManager 的乐观锁、单查询认证，以及业务更新和持久化事件登记的原子回滚。

## PC 管理端

PC-1 已建立独立 [admin/ 工程](admin/README.md)，实现主题布局、员工认证与权限基础。
阶段边界见 [PC-1 契约](docs/PC1_CONTRACT.md)，根 `scripts/verify.sh` 统一执行前后端门禁。
