# Han Menu 架构决策

## 产品定位

这是以苍穹外卖业务能力为参考的全新外卖系统。没有旧用户、旧业务数据或旧客户端契约需要承接。
管理端、Flutter 移动端 App、领域模型、API 和表结构都按新版本设计。当前实现身份模块，其他模块按计划推进。

本项目为个人学习项目，支付测试采用支付宝沙箱，不要求正式商户资质或生产资金交易。
移动端与支付接口的规划边界见 [决策记录](MOBILE_PAYMENT_DECISION.md)；本轮仅调整决策，不提前实现支付。

## 模块化单体与 DDD

一个 Maven 工程构建一个 Spring Boot 应用，使用 Spring Modulith 定义限界上下文，自动校验
公开契约、循环依赖和内部类型访问。业务组织为 identity、customer、shop、catalog、cart、
ordering、payment、notification、reporting 九个模块。

| 分层 | 负责内容 | 实现边界 |
| --- | --- | --- |
| domain | 聚合、值对象、不变量、领域策略、仓储端口 | 纯 Java，聚合通过业务方法改变状态 |
| application | 用例、权限检查、事务与跨模块协作 | 依赖端口，负责加载、调用、保存聚合 |
| infrastructure | 数据存取、缓存、外部服务适配 | Spring Data JPA/Hibernate、Redis 等具体技术 |
| web | 请求校验、身份提取、DTO 映射、HTTP 协议 | Spring MVC，禁止返回 ORM 实体 |
| api / events | 明确发布的同步契约与集成事件 | Spring Modulith NamedInterface |

```mermaid
flowchart LR
  web[HTTP DTO / Controller] --> application[应用用例与事务]
  application --> domain[聚合与端口]
  persistence[JPA 实体 / 仓储适配器] --> domain
  persistence --> orm[Spring Data JPA / Hibernate]
  orm --> db[(PostgreSQL)]
  application --> events[Modulith 集成事件]
  events --> registry[(JPA 事件登记)]
```

权限、价格、订单状态迁移等规则由具备行为的领域对象保护。组合和构造器注入是默认方式，接口
设置在真实变化点，避免机械创建 Service/ServiceImpl、BaseEntity、BaseService 继承体系。

## ORM 与持久化

业务持久化统一使用 **Spring Data JPA 4.1.1 + Hibernate 7.4.5.Final**，版本由 Boot BOM 管理。
Spring Modulith 的事件登记也使用 JPA 实现，与业务更新共享 JpaTransactionManager。

- 常规 CRUD 和查询条件使用 Spring Data 派生方法，不在业务代码中拼装 SQL。
- 批量条件使用 JPA Criteria / Spring Data 4.1 PredicateSpecification，批量删除不加载全部实体。
- 动态组合条件和复杂读取在出现具体需求时使用 Specification、投影和专用查询端口。
- `@Version` 检测并发写入；更新 API 必须提供资源版本，陈旧编辑返回 409。
- Session 使用实体图一次获取员工状态，避免认证时额外懒加载查询。
- `spring.jpa.open-in-view=false`，事务在应用用例中完成，Controller 序列化期间不查询数据库。
- `ddl-auto=validate`，Hibernate 校验映射；Flyway 维护建表、约束和索引的版本化 DDL。
- UUID 由领域用例分配，新增员工实体保持包装类型 version 为 null，由 Spring Data 正确识别为新实体。

### 为什么领域模型与 JPA 实体分开

领域对象需要封装行为，JPA 实体需要配合代理、关联抓取及持久化上下文。两者通过每个持久化模型
的集中映射连接，防止数据库关系、框架注解或懒加载进入领域行为和 HTTP 响应。

这会多一个映射位置，换来清晰的依赖方向；映射集中在实体与仓储适配器，不再分散于每条 SQL 的
参数绑定和 ResultSet 处理中。简单适配器不再额外套通用 RepositoryImpl 或反射 BeanUtils 拷贝。

JPA 关系限于模块内部。不同限界上下文之间只保存稳定业务标识，通过公开 API 或事件协作，不建立
跨模块实体关联、跨模块外键或查询其他模块业务表。

## API 约定

- `/api/v1` 版本化资源路径，HTTP 方法表达操作语义。
- 创建返回 201 和 Location；读取返回 200；成功更新或撤销返回 204。
- 成功直接返回资源 DTO；失败统一为 RFC 9457 Problem Details，扩展 code、traceId。
- 身份认证只支持 `Authorization: Bearer ...`，服务端不从 Cookie 提取认证信息。
- UUID 以 JSON 字符串返回；所有业务时刻使用 Instant 和 ISO-8601 UTC 表达。
- 分页 page 从 0 开始，默认 size=20，上限 100；分页响应不泄露 PageImpl 的框架结构。
- 严格拒绝未知 JSON 字段；资料更新和状态修改的 version 必填。
- 模块异常处理优先于通用兜底，RFC 9457 的扩展字段由工程统一输出。
- 状态使用 ACTIVE、DISABLED 等具名值，不使用业务魔法数字。

具体身份接口见 [P1 契约](P1_CONTRACT.md)。业务参考只用于识别用例，不决定本系统 URI、字段或响应。

## 事务与模块事件

一个应用用例对应明确的事务边界。跨模块强一致操作通过公开契约参与短本地事务；通知、报表等
异步协作用持久化事件表达已提交的业务事实。外部支付网络调用不放入长数据库事务。

JPA 聚合更新和 Modulith 事件登记同事务提交或回滚；消费者必须通过业务键/事件标识实现幂等。
当前单实例配置允许重启重投，后续业务监听器落地时补充有界重试、失败记录及可观测性。
事件登记用于可靠投递，不替代审计日志或事件溯源模型。

未来订单调用支付公开 API，并监听支付结果事件；支付使用不透明业务引用，不反向引用订单模块，
防止形成编译依赖环。通知和报表消费公开事件；报表按需求建设自己的读模型。

## 当前身份边界

identity 只保存账号、显示名称、可选联系电话及认证状态。身份证、性别等不属于当前账号管理的
必需资料，未来真实的人事需求应单独建模，不因参考项目存在字段而自动收集。

P1 的管理员通过初始化创建，普通员工由管理员创建，角色固定为 ADMIN/STAFF；动态角色授权在
出现明确业务需求后扩展。密码使用 BCrypt，随机会话令牌只保存摘要，停用/改密即时撤销旧会话。
Redis 独立限制账号和直连地址请求量；缓存故障不能绕过登录控制。

P3 的顾客认证面向 Flutter App，独立于员工角色及会话权限，不要求微信身份交换。登录方式在 P3
实施前明确，当前不预设短信供应商、第三方登录或将员工会话直接用于顾客业务。

## 数据库基线与演进

当前为未发布的新项目，已重新建立干净基线：V1 身份模块，V2 JPA 事件登记。没有旧账号导入、
演示业务表、兼容表或历史数据转换流程。后续迭代通过新增 Flyway 迁移演进，不依赖 Hibernate 自动改表。

开发初期的架构试验快照保存在被 Git 忽略的本地备份目录，仅用于误操作恢复，不参与应用启动或设计。
项目开发库、测试库与 `pg18_lab` 等其他数据库相互独立。

## 可执行的架构约束

- Modulith 校验模块导出与无环依赖。
- ArchUnit 约束向内依赖、纯领域模型，并禁止生产代码依赖 JdbcClient/JDBC API。
- 真实 PostgreSQL 测试验证 ORM 映射、Hibernate 版本锁和持久化事件的事务回滚。
- 认证实体图的查询数量有验证，关闭 Open-in-View 后接口仍能完整序列化。
- Google Java Format、Checkstyle、中文注释与完整测试作为提交门禁。

参考：[Spring Data 实体持久化](https://docs.spring.io/spring-data/jpa/reference/jpa/entity-persistence.html)、
[派生查询](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html)、
[Modulith 事件机制](https://docs.spring.io/spring-modulith/reference/events.html)。
