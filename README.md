# Han Menu · 外卖系统

以苍穹外卖的业务能力为参考，使用 Java 25、Spring Boot 4.1.1 和 Spring Modulith 2.1.1
构建 DDD 模块化单体。当前交付的是**项目骨架和重构计划**，尚未实现业务接口。

## 先阅读

- [重构计划](docs/REFACTORING_PLAN.md)：源码分析、模块归属、面向对象建模、实施阶段和验收标准。
- [原接口清单](docs/REFERENCE_API.md)：参考版本的 70 个 HTTP 处理方法、源码链接及前后端缺口。
- [中间件准备清单](docs/INFRASTRUCTURE.md)：已有环境、后续需要的资源及接入信息。
- [开发约定](AGENTS.md)：中文注释、Google Java 规范、依赖方向和提交门禁。

## 本地启动

已复用 Podman 的 `pg18`（PostgreSQL 18.6），开发库为 `han_menu`，测试库为 `han_menu_test`。
本地凭证位于被 Git 忽略的 `.env`。JDK 使用 25，Maven Wrapper 固定为 3.9.16。

```bash
# 首次配置；重复执行不会修改已有账号密码或覆盖 .env。
./scripts/setup-local-db.py
./scripts/install-hooks.sh

# 新增中间件：独立 Redis 和 RustFS，不修改已有 PostgreSQL 容器。
python3 scripts/middleware.py up
python3 scripts/middleware.py install-service

# 完整质量检查，包含真实 PostgreSQL 集成测试。
./scripts/verify.sh

# 启动应用，默认监听 127.0.0.1:8080。
./scripts/with-env.sh ./mvnw spring-boot:run
```

`GET /actuator/health` 用于检查应用和数据库连接。Spring Boot 不自动读取 `.env`，IDE 启动时
需配置环境变量；命令行使用 `with-env.sh`。容器名称不同时可以设置 `PG_CONTAINER`。

本机 Redis 8.10.1 位于 `127.0.0.1:6379`，RustFS 1.0.0 的 S3 接口位于 `127.0.0.1:9000`，
控制台为 [RustFS Console](http://127.0.0.1:9001/rustfs/console/)。凭证在 `.env` 中。
执行 `python3 scripts/middleware.py verify` 可验证实际读写；启动、停止及持久化说明见中间件清单。

## 代码布局

```text
com.hanserwei.hanmenu
├── HanMenuApplication
├── identity       账号与权限
├── customer       顾客档案、地址簿
├── shop           门店经营
├── catalog        分类、菜品、口味、套餐
├── cart           购物车
├── ordering       下单、接单、履约、取消
├── payment        支付、退款、渠道回调
├── notification   来单、催单和消息投递
└── reporting      工作台、统计和报表
```

每个模块均有以下包说明文件；只有启动与测试基础设施存在实现类，没有占位控制器、示例业务对象
或虚假的成功接口。业务边界是初始划分，后续用例分析可以调整。

```text
模块/
├── package-info.java    @ApplicationModule：模块声明与依赖限制
├── api/                 @NamedInterface：公开同步契约
├── events/              @NamedInterface：公开集成事件
├── domain/              聚合、实体、值对象、领域服务、仓储端口
├── application/         用例编排、事务边界
├── infrastructure/      持久化、缓存、外部服务适配器
└── web/
    ├── admin/           管理端适配器
    └── app/             顾客端适配器
```

业务模块初始 `allowedDependencies = {}`，实现具体契约时才按白名单开放依赖。
架构测试允许暂时为空的分层包，但对未来加入的实现类执行相同的依赖检查。

## 当前已就绪的基础能力

- Spring Modulith 模块边界检查、启动时验证和自动模块文档。
- PostgreSQL 连接、Flyway 迁移、JDBC 事件登记基础设施。
- Java 虚拟线程、Jackson 3、请求校验依赖、Problem Details 基础配置。
- Google Java Format 1.36.1、Checkstyle 14.1.0 官方 Google 规则、Git 提交钩子及 CI。
- 模块架构测试，以及使用隔离 PostgreSQL schema 的真实 HTTP 启动和迁移测试。

认证、缓存、图片上传、具体领域对象和业务 API 均属于后续阶段，尚未接入。
事件表已准备好；尚无业务发布者和监听器，当前不能宣称已经具备订单或支付的可靠投递流程。

## 开发与提交

```bash
./mvnw spotless:apply    # 自动格式化 Java 源码
./mvnw validate          # 格式检查和 Google Checkstyle 扫描
./scripts/verify.sh      # 每次编码完成后、提交前必须通过
```

提交钩子会执行 `clean verify`，警告级 Checkstyle 违规也阻止提交，并扫描测试源码。
新克隆需要手动安装钩子；远程仓库建立后，还需将 CI 的 `verify` 作业配置为分支保护必需检查。
CI 配置已经入库，目前尚未运行远程 CI。

中文 Javadoc 描述职责、约束、参数和重要副作用。为兼容 Google Checkstyle 原始规则，摘要首句
以英文句点 `.` 结束，正文使用中文标点；检查规则保持启用。Maven 自动生成文件和已执行的历史
迁移文件保留原内容，不为翻译注释而修改校验和。

检查报告位于 `target/checkstyle-result.xml`、`target/surefire-reports/`、
`target/failsafe-reports/`，模块图及说明位于 `target/spring-modulith-docs/`。

## 数据库历史

上一版演示业务代码与业务测试已移除。V1、V2 已在开发库执行，因此保留历史迁移文件；V3 将原表
重命名为 `legacy_demo_catalog_dish`、`legacy_demo_menu_entry`，保留任何已有数据。
这些表不归属新业务模型，当前应用不访问它们。正式业务表随阶段实施新增迁移，不从示例模型演化。

集成测试使用名称以 `_test` 结尾的专用数据库，每个测试上下文创建随机 schema，关闭后清理。
不使用 H2 替代 PostgreSQL，也不会在数据库不可用时跳过测试。
