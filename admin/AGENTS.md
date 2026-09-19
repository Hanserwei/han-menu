# 管理端开发约定

- 本工程使用 Vue 3 + TypeScript + antdv-next + pnpm；保留浅鼠尾草／墨绿设计，参照 `../docs/design/admin/`。
- 按业务模块组织：`app` 装配应用，`modules` 实现业务用例，`shared` 提供跨业务的请求、组件和工具。
- `shared` 不导入 `modules/app`；业务模块不导入 `app`；跨模块通过 `index.ts` 公开入口访问。
- 页面使用异步加载函数导出，不把所有页面静态导入应用布局。不得创建机械的 BaseService/BaseStore 体系。
- 中文注释说明模块职责、权限、会话状态、并发与取消语义；复杂行为必须注明原因，避免逐行翻译代码。
- 依赖选择最新兼容稳定版，Node 使用 LTS。固定直接依赖与 pnpm 版本，提交 lockfile，不用忽略 peer 校验强装。
- 请求类型从 `contracts/openapi.json` 生成；修改后运行 `pnpm api:generate`，不手改生成文件。
- 员工令牌仅使用内存与 sessionStorage。恢复必须 GET /me；不信任持久化角色，不解析 JWT。
- 新业务请求必须经过统一客户端，401 撤销会话，403 显示权限错误；退出要取消旧请求并清除个人查询缓存。
- 不将账号密码、手机号、认证头或请求正文写日志。前端环境变量只存公开配置。
- 状态变更以服务端返回为准，不默认乐观成功；带版本的资源修改必须提交当前版本，409 保留必要输入。
- 验证命令为 `pnpm verify` 与独立测试库的 `pnpm test:e2e`；仓库根 `scripts/verify.sh` 统一执行后端及前端门禁。
- 真实浏览器测试只允许 `_test` 数据库和临时 schema，不使用开发管理员执行改密、停用或造数据。
