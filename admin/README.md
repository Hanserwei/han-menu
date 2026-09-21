# HAN MENU 管理端

PC-1 身份基础、PC-2 经营资料、PC-3 订单作业及 PC-4 实时通知，Vue 3 + TypeScript + antdv-next。独立 pnpm 工程，构建产物是 `dist/`；后端仍单独打包 JAR。

## 当前能力

- 浅鼠尾草／墨绿主题、中文组件语言、桌面与窄屏导航、管理员与员工菜单隔离。
- 员工登录、刷新恢复、到期退出、403、服务故障恢复、本人改密与服务端退出。
- 统一 Bearer、RFC 9457、超时、请求取消、会话代际和查询缓存清理。
- 真实工作台及门店摘要；员工、顾客、分类、菜品、套餐、图片关联与门店管理已开放。
- 订单检索、快照详情、接单／拒单／取消／配送／完成、工作台待办与顾客关联订单已开放。
- 来单／催单实时提示、通知补查、本人阅读确认、断线恢复及HTTP降级已开放。
- 报表、资金与管理员通知投递诊断按后续阶段推进。
- 开发模式 `/_dev/components` 提供 Table/Form/DatePicker/Upload/Drawer 兼容性验收；生产构建不包含此路由和页面。

## 环境与启动

使用 Node **24.21.0 LTS**、pnpm **12.4.2**。版本固定在 `.node-version` 与 package.json；pnpm 在 Bash 可用，亦可在 fish 中执行。
使用 fnm 时，首次运行 `fnm install 24.21.0`，再执行 `fnm use 24.21.0`。pnpm 会依据 packageManager 使用项目版本，不要求全局替换已有版本。

在仓库根启动后端：

```bash
./scripts/with-env.sh ./mvnw spring-boot:run
```

另一个终端进入 `admin/`：

```bash
pnpm install --frozen-lockfile
pnpm dev
```

浏览器访问 http://127.0.0.1:5173，使用已有员工账号。Vite 将 `/api` 代理到 127.0.0.1:8080。
需要其他地址时复制 `.env.example` 到 `.env.local` 后修改 `ADMIN_API_TARGET`。不要复制后端私钥或数据库凭证到管理端。

## 分包结构

```text
src/
  app/                    # 启动装配、主题、布局、路由、开发组件验收页
  modules/
    auth/                 # 身份 API、会话状态机、登录和账号页
    workspace/            # 工作台和公开门店摘要
    notifications/        # 独立Socket运行器、协议、个人阅读与页面
    orders/               # 订单查询、状态、详情生命周期与履约
    catalog/              # 分类、菜品、套餐、图片与规格草稿
    employees/            # 员工资料与启停用
    customers/            # 顾客档案查询与启停用
    shop/                 # 门店资料与营业状态
  shared/
    api/                  # 生成类型、统一传输、Problem、会话端口、查询客户端
    ui/                   # 与业务无关的公共界面
    lib/                  # 统一时间展示等纯工具
contracts/openapi.json    # 已核对的后端契约基线
scripts/                  # 类型生成、边界检查、隔离浏览器测试后端
```

`index.ts` 是模块公开入口，页面通过加载函数保留分包。`check:boundaries` 会检查依赖方向。
OpenAPI 类型检查不替代身份字段运行时验证。Pinia 只管理会话状态；服务端工作台数据由 Vue Query 管理。

## 验证

```bash
pnpm verify
pnpm exec playwright install chromium
```

真实浏览器测试需要已经构建的后端 JAR，以及根 `.env` 中的 TEST_DB_*、Redis 和 RustFS 测试配置。在仓库根运行：

```bash
./scripts/verify.sh
```

该命令先执行后端全量验证，再执行前端格式、契约类型、lint、模块边界、类型、单元测试、生产构建与浏览器测试。
前端测试后端固定使用 18081，测试 Vite 使用 15173，不复用正在运行的 8080/5173。测试在 `_test` 数据库创建随机 schema，
应用退出后删除该 schema 及测试桶中该 schema 的图片前缀；固定测试密码只适用于临时测试账号，不能用于开发或部署账号。

只重跑浏览器测试可在根目录运行：

```bash
./scripts/with-env.sh pnpm --dir admin test:e2e
```

如果 Bash 的 pnpm 没有激活正确 Node，可使用 `fnm exec --using 24.21.0 pnpm --dir admin ...`，或切换到已配置的 fish。

## 契约更新

后端启动后，从公开 `/v3/api-docs` 更新 `contracts/openapi.json`，核对变动并运行 `pnpm api:generate`。
`pnpm api:check` 在无后端运行的情况下验证快照与生成类型一致；后端接口变更应在同一变更中更新快照。
快照不是 mock 服务，实际应用始终访问后端；请求失败不会展示示例数据。

## 依赖选择

2026-09-19 核对官方 npm 发布元数据后采用 Vue 3.5.43、Vite 8.3.0、Vue Router 5.3.1、Pinia 4.0.3、
antdv-next 1.5.4、Vue Query 5.103.1、Vitest 5.0.1、Playwright 1.63.0。大多数前端包没有 LTS 标签，使用稳定发布渠道。

TypeScript 采用 **5.9.3**：openapi-typescript 7.13.0 声明 TypeScript ^5.x，typescript-eslint 8.70.0 声明 <6.1；
因此没有使用不满足共同约束的 TypeScript 7.0.2。升级时同时检查类型生成器、Vue 类型工具和 ESLint。
保持 strict-peer-dependencies，不通过关闭检查或 overrides 伪造兼容性。

vue-demi 的安装脚本只切换 Vue 适配版本，已显式登记允许构建。pnpm 自动登记的两个新发布精确版本例外随工作区配置提交，
没有全局取消依赖审查。生产资源不包含源码映射、后端配置或测试账号。

依据：[Node 发布周期](https://nodejs.org/en/about/previous-releases)、[Vue 官方脚手架](https://vuejs.org/guide/quick-start.html)、
[antdv-next](https://github.com/antdv-next/antdv-next)、[openapi-typescript 包元数据](https://www.npmjs.com/package/openapi-typescript)。

## 部署

`pnpm build` 后仅部署 dist。Nginx 使用 history fallback：页面路径回退 index.html，`/api/` 代理后端，API 错误不可回退 HTML。
保持 HTTPS 同源代理；不要直接把 `pnpm dev` 当生产服务器。生产代码不包含开发组件验收页；员工登录后创建唯一通知连接，按一次性票据及HTTP补查协议恢复。
WS代理需要升级头和后端精确允许的前端Origin，不能用通配符或把票据放URL。

PC-2 约束和验收见 [阶段契约](../docs/PC2_CONTRACT.md)。

PC-3 行为与测试边界见 [阶段契约](../docs/PC3_CONTRACT.md)；付款状态测试事实只写入运行器创建的临时测试 schema，不提供任何模拟支付 HTTP 接口。

PC-4 详见 [阶段契约](../docs/PC4_CONTRACT.md)。开发后端配置 `NOTIFICATION_ALLOWED_ORIGINS=http://127.0.0.1:5173`，
前端Origin必须与实际访问地址一致；后台任务启用后提供真实推送和员工会话撤销检查。
