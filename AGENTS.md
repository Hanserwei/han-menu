# 项目开发约定

## 目标与范围

- 项目是面向完整外卖业务的 DDD 模块化单体，业务参考见 `docs/REFACTORING_PLAN.md`。
- 本项目是全新版本，没有历史业务数据、用户、旧接口或旧客户端需要兼容。
- 顾客移动端使用 Flutter App，不开发微信小程序；顾客认证不依赖微信 openid/code。
- 支付测试仅对接支付宝沙箱，不以生产支付或正式商户资质作为交付前提。
- P5 已实现支付宝沙箱支付创建、查询、通知、关单与全额退款；渠道签名和验签在服务端，不能信任 App 返回的支付成功。
- Flutter 工程与移动端 SDK 真机联调留在 P7；禁止提供模拟支付成功接口。
- P1 包含身份认证、员工权限、全新资源 API、ORM 持久化、审计及接口文档。
- P1—P6 后端已实现；P4/P5/P6 契约分别见 `docs/P4_CONTRACT.md`、`docs/P5_CONTRACT.md`、`docs/P6_CONTRACT.md`。
- P7 管理端前置接口补充见 `docs/P7_BACKEND_CONTRACT.md`；顾客管理、支付／退款及安全审计查询仅允许当前管理员，订单履约允许员工。
- PC-1—PC-6 管理端工程位于 `admin/`，采用 Vue 3 + TypeScript + antdv-next + pnpm，细节见 `admin/AGENTS.md` 与 `docs/PC1_CONTRACT.md`、`docs/PC2_CONTRACT.md`、`docs/PC3_CONTRACT.md`、`docs/PC4_CONTRACT.md`、`docs/PC5_CONTRACT.md`、`docs/PC6_CONTRACT.md`；Flutter 环境准备暂缓。
- 顾客与员工使用独立会话及安全链；购物车仅通过 customer/catalog 的公开 API 访问业务能力；订单通过 customer/shop/catalog/cart 的公开 API 结算，并通过 payment 的公开 API 和结果事件协作；payment 不反向依赖 ordering。
- 通知通过公开订单事件持久化，WebSocket 使用一次性票据并复验员工会话；统计仅使用公开事件/API 建立可重建投影。
- 使用 JDK 25 和已提交的 Maven Wrapper，一个工程构建一个可执行 JAR。
- 先按限界上下文组织模块，再在内部划分 domain/application/infrastructure/web。

## 面向对象与 DDD

- 聚合根通过明确的业务方法维护状态和不变量，不通过任意 setter 改变业务状态。
- 值对象优先不可变，可使用 record 并在构造时验证约束；record 不代替有生命周期的聚合。
- 应用服务负责用例编排和事务；领域对象负责业务规则，避免把所有判断堆进 ServiceImpl。
- 使用构造器注入、接口隔离和组合，按真实变化点引入策略；不为所有类机械创建接口。
- 仓储和外部能力通过端口依赖倒置，不引入无业务意义的 BaseEntity/BaseService 继承体系。
- domain 仅依赖 Java 和本模块领域类型，不引入 Spring、SQL、HTTP、校验框架或外部 SDK。
- 跨模块只使用明确导出的 api/events，不访问其他模块内部类、业务表或建立跨模块外键。
- 业务状态与事件登记同事务提交；消费者负责幂等。外部支付请求不放入长数据库事务。
- 业务持久化使用 Spring Data JPA/Hibernate；禁止业务代码依赖 JdbcClient 或直接编写 JDBC。
- JPA 实体、查询与映射位于 infrastructure/persistence；普通查询用派生方法，组合条件用 Specification。
- 关闭 Open-in-View，Hibernate 只校验结构；表、约束、索引由 Flyway 维护。
- DTO、持久化对象、领域对象职责分离，映射集中完成，不能用反射拷贝绕过领域规则。
- 新 API 使用 /api/v1、Bearer、UUID、UTC 时刻和 RFC 9457；修改资源必须带版本，不增加旧协议别名。
- 模块专用异常 Advice 使用 Order(0)，通用兜底使用 Order(100)，避免通用 Exception 处理抢占业务错误。
- 应用和适配层可使用 Guava、Commons Lang3 简化通用操作；领域层继续保持框架与工具库无关。
- 凭证类型必须提供脱敏字符串表示；审计使用固定事件类型及内部标识，不记录请求体或认证头。
- 员工会话以数据库状态为准，Redis 限流不可用时拒绝登录；不得降级为放行。
- 公开目录缓存可回源数据库，使用同事务目录修订号隔离代际；不得以缓存控制商品或门店业务状态。
- 目录写操作持有修订行锁完成分类/套餐引用检查；图片存储调用不占用数据库长事务，失败需补偿。

## 中文注释和 Google Java 规范

- 自编 Java 代码使用中文包说明、类型和方法 Javadoc；关键逻辑说明设计原因和约束。
- Javadoc 摘要先描述职责，再按需解释不变量、事务边界、幂等、并发或资源生命周期。
- 参数、返回值、重要业务异常应有明确说明；不添加无内容的 @param/@return/@throws。
- Google 默认 SummaryJavadoc 规则要求摘要首句以英文句点 `.` 结束；正文使用中文。
- 注释随行为同步更新，不堆砌逐行翻译或保留已失效的描述。
- 自动生成的 Maven Wrapper 保留原样；当前未发布的新项目基线已按用户要求重建。
- 后续数据库演进使用新增 Flyway 迁移，不自动清空已形成业务数据的数据库。
- Java 编辑后运行 `./mvnw spotless:apply`，不得关闭 Google 规则来放行违规代码。

## 验证与提交

- 每次编码完成后、提交前必须通过 `./scripts/verify.sh`：格式、Checkstyle、架构测试和真实数据库集成测试。
- 不得使用 `--no-verify`、跳过测试或降低已有实现类的架构约束以通过构建。
- 当前空骨架允许 ArchUnit 的空匹配集合；加入类后依赖规则必须完整执行。
- 克隆后运行 `./scripts/install-hooks.sh`。远程仓库建立后将 CI 验证设为必需检查。
- 密钥仅保存在被忽略的 `.env` 或环境变量；集成测试必须使用独立测试库和临时 schema。
- 优先选用稳定版本和 Spring Boot/Modulith BOM；引入新依赖前核对兼容性并完整验证。
