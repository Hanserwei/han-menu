# P7 客户端技术选型与开发路线

日期：2026-09-19。状态：技术选型、UI 设计及 PC-1—PC-4 已实现，见 [PC-1 契约](PC1_CONTRACT.md)、[PC-2](PC2_CONTRACT.md) 、[PC-3](PC3_CONTRACT.md) 与 [PC-4 契约](PC4_CONTRACT.md)。
P1—P6 后端基线已完成；用户已授权并完成 PC-1—PC-4，管理端位于当前仓库 `admin/`，独立 pnpm 构建。管理端前置后端补充见 [接口补齐契约](P7_BACKEND_CONTRACT.md)。
Flutter 环境配置按用户最新指令暂缓，不影响先完成管理端后端和 PC 开发。
PC UI 已选第三套「温润经营台」，开发设计规范、主题参数与页面图见 [管理端设计交付](design/admin/README.md)。

## 1. 工程划分与推进顺序

确定 PC 管理后台与顾客 App 分开开发，使用两套 UI、两套状态管理和独立发布流程。

工程独立构建；当前 PC 管理端按用户决定放在本仓库 `admin/`，后端保留现有结构：

| 工程 | 定位 | 技术与产物 |
| --- | --- | --- |
| han-menu | 现有共享后端 | Java 25 模块化单体，可执行 JAR |
| admin/（包名 han-menu-admin） | PC 员工管理后台 | Vue 3 + TypeScript，静态 Web 产物 |
| han-menu-app | 顾客移动 App | Flutter + Dart，优先 Android 安装包 |

独立工程和构建发布是本轮确定的边界；仓库名称／拆仓方式为建议布局，实施前可调整而不影响技术路线。
两端共享 API 契约、状态定义、错误码和品牌规范，各自实现请求模型和交互，不共享 TS/Dart 页面代码。
前端不直接访问数据库，也不在客户端复制后端支付、计价和订单状态规则。

推进顺序为：PC 管理端形成可用闭环 → Android 环境及支付技术验证 → Flutter 顾客端完整流程 →
双端联调和交付。PC 与 App 分别验收，PC 构建与测试不依赖 Flutter SDK。

## 2. PC 管理端选型

| 领域 | 选型 | 用途与边界 |
| --- | --- | --- |
| 框架与语言 | Vue 3.5+ + TypeScript strict | Composition API、script setup，页面与业务逻辑分离 |
| 构建与初始化 | Vite + 官方 create-vue | SPA，按业务路由懒加载 |
| 运行与包管理 | Node.js 24 LTS + pnpm | 使用项目锁文件和明确工具版本，避免不同机器漂移 |
| UI 组件库 | antdv-next | 表格、表单、日期筛选、弹窗、抽屉、分页、上传和反馈 |
| 页面路由 | Vue Router | 路由元信息定义 ADMIN/STAFF 可见范围 |
| 本地应用状态 | Pinia | 当前身份、会话生命周期和界面偏好 |
| 服务端数据状态 | TanStack Vue Query | 列表／详情缓存、请求去重、分页、刷新和失效处理 |
| HTTP 与类型契约 | openapi-typescript + openapi-fetch | 从已验收 OpenAPI 生成 TS 类型，统一处理 Bearer、异常和取消请求 |
| 图表 | Apache ECharts | 营业额、订单、顾客增长和销量展示；统计数值来自后端 |
| 样式 | antdv-next 主题配置与 Design Token + 局部 scoped 样式 | 组件主题使用其 CSS-in-JS 能力，统一颜色、间距与管理台信息密度 |
| 时间 | Day.js 的 UTC/timezone 能力 | 区分 UTC 业务时刻与 Asia/Shanghai 经营日期 |
| 规范与验证 | ESLint、Prettier、vue-tsc、Vitest、Playwright | 静态检查、逻辑／组件测试和真实端到端流程 |

按用户选择，UI 组件库确定为 `antdv-next`，采用其基于 Ant Design 的视觉体系。
表格、编辑表单、状态操作和权限菜单使用该库组件，主题统一管理主色、圆角、字号和组件密度，
避免逐页覆盖内部 CSS。具体组件 API 以 antdv-next 官方文档为准。
工程用 Vue 官方脚手架建立小而清晰的应用骨架，只封装实际复用的搜索栏、分页、状态标签和权限动作组件。
PC-1 先验证 Table、Form、DatePicker、Upload、Drawer 的类型、中文语言配置、主题和交互，
锁定相互兼容的稳定版本后再扩展业务页面。

Pinia 与 Vue Query 职责分开：登录身份和侧栏偏好属于本地状态；订单、员工、商品、统计等
服务器数据交给查询缓存。修改成功后使关联查询失效；退出和切换账号时取消旧请求、清理缓存和连接。

openapi-typescript 提供编译期类型，不能代替运行时的错误处理或关键数据校验。生成文件不手工修改，
业务模块在其外部封装调用和页面模型。锁定一份后端 OpenAPI 基线，接口变更要同步重新生成并验证。
具体依赖补丁版本在 PC 工程初始化时核对相互兼容的稳定版，再锁入 lockfile。

## 3. PC 信息架构、权限与真实接口边界

| 页面组 | 使用角色 | 首版范围 |
| --- | --- | --- |
| 工作台 | ADMIN / STAFF | 待接单、已接单、配送、取消／退款处理中及门店目录摘要 |
| 订单中心 | ADMIN / STAFF | 状态／UUID／顾客／收货电话／创建时间筛选，详情与履约动作 |
| 通知中心 | ADMIN / STAFF | 来单／催单提醒、补查、个人阅读进度 |
| 商品管理 | ADMIN | 分类、菜品、口味、套餐组成、图片、上下架 |
| 门店设置 | ADMIN | 资料与营业状态 |
| 员工管理 | ADMIN | 新增、编辑、启停用员工；不虚构动态角色配置 |
| 顾客管理 | ADMIN | 档案列表、详情、启停用和关联订单查询 |
| 支付与退款 | ADMIN | 流水筛选、详情、原订单与支付引用追踪 |
| 安全审计 | ADMIN | 身份安全事件、操作者、目标、结果与发生时间检索 |
| 经营分析 | ADMIN | 经营日账、顾客增长、销量、资金对账与 XLSX 下载 |
| 系统维护 | ADMIN | 通知投递轨迹／重投、统计投影状态／重建 |
| 我的账号 | ADMIN / STAFF | 当前身份、改密和退出 |

管理端所需接口已逐项核对，本轮补充了订单组合检索、顾客档案与启停用、支付／退款流水查询和
身份安全审计检索。具体页面—权限—接口清单、参数、响应及边界见 [管理端前置后端契约](P7_BACKEND_CONTRACT.md)。

订单检索以 UUID 为订单编号，电话使用成交时的收货快照；管理端财务查询使用员工安全链下的管理员接口。
安全审计只展示实际登记的身份安全事件。PC-0 按该契约设计页面，不用当前页前端过滤替代数据库组合查询。

## 4. PC 实施阶段与验收

| 阶段 | 开发内容 | 完成标准 |
| --- | --- | --- |
| PC-0：契约和页面规划 | 菜单、权限、页面/API 矩阵、订单动作表、主题和桌面布局规范 | 每个页面和动作均有明确接口与权限；缺口单独登记 |
| PC-1：工程和身份基础 | 工程初始化、antdv-next 核心组件及主题验证、应用布局、路由、请求层、登录、会话恢复、改密、退出 | 核心组件验证通过；ADMIN/STAFF 真实登录；401 清理会话，403 正确显示权限错误 |
| PC-2：经营基础资料 | 员工、顾客档案及启停用、分类、菜品、口味、套餐、图片、门店 | 完成真实增改／上下架流程，表单校验、分页和版本冲突有明确反馈 |
| PC-3：订单作业闭环 | 工作台、状态列表、详情、接单／拒单／取消／配送／完成 | 全流程以后端状态为准；错误状态不能操作，提交时有防重复和并发处理 |
| PC-4：实时通知 | 一次性票据、WebSocket、通知列表、阅读确认、重连补查 | 断线和重复消息不丢失阅读进度；退出关闭连接；失败可回退到定期补查 |
| PC-5：经营分析与维护 | 支付／退款流水、报表图表、销量、对账、导出、安全审计、投递诊断、投影重建 | 图表口径与后端一致，导出直接使用后端 XLSX；维护动作有影响说明与确认 |
| PC-6：交付验收 | 关键端到端测试、桌面适配、错误恢复、构建及部署文档 | 浏览器中真实完成主要管理流程，可独立构建和部署 |

布局以桌面操作效率为主：侧栏导航、页面标题／面包屑、筛选栏、数据表格与详情／编辑抽屉。
覆盖常见 1366、1440、1920 宽度，优先验证 Chrome/Edge。页面必须具有加载、空数据、失败和
无权限状态；读写较多的长表单避免因轮询自动刷新而覆盖尚未提交的编辑内容。

PC 验收重点：

- 员工会话是 hme_ 不透明令牌，不解析 JWT，也不实现不存在的刷新令牌接口。
- 首版建议使用内存状态配合 sessionStorage 做当前浏览器会话恢复，初始化时通过 /me 重验；
  不提供长期自动登录。客户端保存的角色只控制界面，权限最终由服务器决定。
- 接单、删除、上下架、取消等变更携带最新 version；409 保留必要编辑内容并提示重新读取，
  不自动重复执行写请求。401、403、429、503 分别处理，错误详情可展示 traceId。
- CANCELLING 与 REFUNDING 显示处理中，不能把已发出请求展示为已取消／已退款。
- WebSocket 提示触发 HTTP 补查，从真实已处理游标前移；不直接用最新帧的序号跳过中间消息。
  每次重连重新取票据，应用实例只维护一条通知连接，路由切换不会重复建连。
- 财务数据、经营日期和营业额口径遵循 P6；金额展示不通过浮点加总重新构造支付金额。
- 图表按需加载，表格使用后端分页，XLSX 由后端生成后下载。

## 5. Flutter 顾客端方向

确定使用 Flutter + Dart，先做 Android，iOS 在获得 macOS/Xcode 环境后单独验收。

| 领域 | 建议选型 | 说明 |
| --- | --- | --- |
| UI | Flutter Material 3 + 统一主题 | 以菜单浏览、加购、结算、订单追踪等 C 端流程组织 |
| 状态与依赖管理 | Riverpod | 统一异步状态、依赖生命周期及测试替换；初期使用基础稳定 API |
| 路由 | go_router | 登录跳转、订单详情和导航栈 |
| 网络 | Dio | Bearer、超时、请求取消、统一错误处理 |
| 数据模型 | json_serializable | 生成 JSON 映射，业务规则和状态判断保持明确 |
| 凭证存储 | flutter_secure_storage | 顾客 hmc_ 会话使用平台安全存储 |
| 验证 | flutter analyze、单元／Widget／integration_test | 覆盖页面逻辑、导航及真实业务流程 |

按功能模块组织 auth、menu、cart、addresses、orders、payment、profile。每个模块区分页面、
状态／ViewModel 和数据 Repository/API 调用；先保持轻量，不机械照搬 Java 后端的四层 DDD 目录。
考虑当前 Flutter 经验为零，先通过小范围可运行功能熟悉 Widget、布局、Future、路由和异步状态，
每阶段都保留能在设备运行的版本。

| 阶段 | 内容 | 验收 |
| --- | --- | --- |
| APP-0：环境和基础 | Flutter/Android 工具链、设备、主题与导航基础 | doctor 检查和设备识别通过，能运行及热重载 |
| APP-1：支付技术验证 | Android 沙箱 SDK 唤起、回跳、App 恢复后服务端查单 | 尽早确认关键原生支付链路，避免所有页面完成后才发现桥接问题 |
| APP-2：浏览与选购 | 顾客登录、公开菜单、商品规格、购物车、地址簿 | 服务端目录／购物车版本和资源归属正常工作 |
| APP-3：交易生命周期 | 下单、支付、历史、详情、取消、退款状态、再来一单、催单 | 真实订单和沙箱交易闭环，重复点击和网络重试不能重复建单／付款 |
| APP-4：恢复和交付 | 会话到期、断网、切后台、重启恢复、设备适配与打包 | 设备端完整流程和异常场景通过，生成可安装交付包 |

支付调用通过独立适配层封装；具体 Flutter 支付桥接包在 APP-1 核对维护情况、沙箱能力和回跳
行为后锁定。若候选包不能满足要求，采用官方 Android SDK 的小型平台通道适配。这个适配层接收
后端签名参数，不持有支付宝私钥。SDK 返回成功只是触发查单的信号，订单状态仍以服务端为准。
Flutter Web 可用于部分界面开发，不能替代 Android/iOS 原生支付验收。

## 6. 本机环境检查与用户准备事项

2026-09-19 只读检查结果：当前开发主机为 Linux；Node v24.16.0、npm 11.13.0、pnpm 11.5.0 可用。
PATH 未发现 flutter、dart、adb、sdkmanager、studio；也未发现检查过的常用 Flutter SDK 和
~/Android/Sdk 目录。该结果说明目前没有检测到可验证的 Flutter/Android 工具链；如果安装在其他
位置，应以实际 SDK 路径和 doctor 结果继续确认，不能仅凭 PATH 断言电脑从未安装 Android Studio。

PC 开发可以先推进。进入 APP-0 前，需要用户配置：

1. Flutter stable SDK，并把其 bin 加入 PATH；Flutter SDK 已提供配套 Dart。
2. Android Studio，以及 Flutter/Dart 编辑器插件。
3. Android SDK Platform、Build-Tools、Platform-Tools、Command-line Tools；使用模拟器时配置 Emulator。
4. 一台开启 USB 调试的 Android 真机，或可运行的 Android 模拟器。沙箱原生支付优先在实际支持的 Android 设备验证。
5. 按官方引导确认 Android SDK 许可，完成设备连接。

准备完成后以以下检查作为交接条件：

- flutter --version 能显示稳定通道 SDK。
- flutter doctor -v 中 Flutter、Android toolchain 和使用的 Android 开发环境正常。
- flutter devices 能识别目标 Android 设备。
- 首个应用能运行、热重载和打包；仅做 Android 时不要求 Linux 桌面或 iOS 工具链全部通过。

Android 构建 JDK 与后端 JDK 分开管理，按 Android Studio／AGP／Gradle 的兼容要求配置。
后端继续使用 JDK 25，不通过全局替换后端 JAVA_HOME 来解决 Flutter 构建问题。
iOS 构建和真机联调需要 macOS 与 Xcode；本机 Linux 上先完成 Android。

手机还需要能访问开发后端，支付宝需要可达的通知地址。P5 的临时通知通道已经清理，正式进行
APP-1 联调时重新配置有效的 HTTPS 回调地址和设备可达 API 地址，不复用失效的临时 URL。

## 7. 集成、测试与发布约定

- PC 的类型检查、lint、测试、构建、端到端验收独立于 Flutter；App 有自己的分析、测试和 Android 构建流程。
- 后端继续使用现有完整 verify.sh 门禁。P7 如补充 API，仍遵守 DDD、迁移和后端测试要求。
- PC 静态产物建议由 Nginx 提供，与 API 同源代理，同时配置 WebSocket 升级和受信任代理／Origin。
- App 通过环境配置访问后端 HTTPS API，顾客与员工会话始终独立。
- 前端环境变量只放公开配置，不把支付宝私钥、数据库凭证或后端管理密钥放入构建产物。
- 真实验收采用独立测试账号、测试数据和支付宝沙箱，不用假成功状态代替后端或设备联调。

PC-0 设计、PC-1 工程基础与 PC-2 经营基础资料已交付；PC-3 订单作业也已交付，PC-4 实时通知也已交付，后续进入 PC-5 经营分析与维护。Flutter 环境准备暂缓。

## 参考依据

- [Vue 官方工具链](https://vuejs.org/guide/scaling-up/tooling.html)：create-vue、Vite 与 TypeScript 工具。
- [antdv-next 官方站点](https://www.antdv-next.com/)、[官方仓库](https://github.com/antdv-next/antdv-next)：Vue 3、TypeScript、Ant Design 视觉体系及 CSS-in-JS 主题。
- [antdv-next 官方 Playground](https://github.com/antdv-next/antdv-next-playground)：Vue 3.5+ 环境要求。
- [Pinia](https://pinia.vuejs.org/introduction.html)、[TanStack Vue Query](https://tanstack.com/query/latest/docs/framework/vue/overview)。
- [OpenAPI TypeScript / openapi-fetch](https://openapi-ts.dev/openapi-fetch/)。
- [Apache ECharts](https://echarts.apache.org/handbook/en/get-started/)、[Vitest](https://vitest.dev/guide/)、[Playwright](https://playwright.dev/docs/intro)。
- [Flutter 架构建议](https://docs.flutter.dev/app-architecture/recommendations)、[Riverpod](https://riverpod.dev/docs/introduction/getting_started)。
- [Dio](https://pub.dev/packages/dio)、[flutter_secure_storage](https://pub.dev/packages/flutter_secure_storage)。
- [Flutter 安装](https://docs.flutter.dev/install)、[Android 环境设置](https://docs.flutter.dev/platform-integration/android/setup)。
- [Android 构建 JDK](https://developer.android.com/build/jdks)。
