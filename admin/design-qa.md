# PC-1 设计与交互验收

日期：2026-09-19。验收范围为本次获准的 PC-1 工程与身份基础，不将 PC-3 工作台订单表或 PC-4 通知功能视为已交付。

## 视觉依据与状态

- 选定源：`../docs/design/admin/assets/workspace.png`，1489 × 1056。
- 规范：`../docs/design/admin/UI_SPEC.md` 和主题参数；精确 CSS 尺寸以规范为准。
- 最终浏览器图：`../docs/design/admin/verification/pc1-workspace-final.png`，1440 × 1024 CSS px，deviceScaleFactor=1。
- 原图仅在对照副本中缩放为 1440 × 1024；不改写源图。全幅对照尺寸 2880 × 1024。
- 状态：ADMIN 已登录，真实独立测试 schema，零订单、门店打烊。原稿的演示营业状态、数量、员工姓名不作为真实接口值。
- Codex 内置浏览器手动核验登录、工作台、账号菜单及账号页；最终调整由相同尺寸 Chromium 浏览器验收重拍。

## 对照证据

- 首次全幅：`../docs/design/admin/verification/pc1-comparison.png`。
- 首次局部：`../docs/design/admin/verification/pc1-comparison-detail.png`。
- 修正后全幅：`../docs/design/admin/verification/pc1-comparison-final.png`。
- 修正后局部：`../docs/design/admin/verification/pc1-comparison-detail-final.png`。
- 登录／账号：`../docs/design/admin/verification/pc1-login.png`、`pc1-account.png`。
- 1366 × 768：`../docs/design/admin/verification/pc1-workspace-1366.png`；未隐藏持续可用的页面控件。

源图和浏览器图放入同一对照图中检查；另放大左侧导航、标题与指标区域，核验字体、选中状态和间距。
PC-1 只展示真实摘要，把原图订单表区域暂作今日概况；后续入口禁用且不显示虚假的通知连接状态。此为阶段范围差异，
不是宣称完整工作台图已经一比一落地。

## Findings 与修正历史

1. [P2，已修正] 初始导航首组额外增加了 20px 上间距，选中项低于设计节奏。
   调整首组间距后重新拍摄，上述 final 全幅与局部图证实品牌区与导航更紧凑，页面没有额外横向溢出。
2. [已修正] 兼容性运行发现旧 Drawer.width API 警告，改用当前 size API；新一轮浏览器流程未再出现组件警告。
3. [已修正] 恢复错误中的“重试”按钮被组件自动插入中文字间距，补充明确 aria-label，恢复流程验证通过。

## 五项视觉检查

- 字体：正文使用系统中文无衬线字体栈，字标使用衬线；标题、指标和辅助文字遵循设计参数。
- 间距：侧栏 208px、顶栏 64px、主留白 32px；窄桌面切换折叠导航，不裁切操作区。
- 颜色：浅鼠尾草侧栏、墨绿选中项与按钮；取消／退款处理中使用琥珀色；禁用项有明确不可用状态。
- 图片与图标：PC-1 无商品图片或插画；采用 @antdv-next/icons 真实组件图标，没有代码绘制的替代图片。
- 文案：使用后端真实状态及角色；失败不以零数据替代；未开放页面不伪装成功、没有阶段编号混入主要业务操作。

## 交互验证

20 项单元测试、6 项真实后端浏览器测试通过。手动内置浏览器检查控制台无新增 error/warn。
覆盖登录、刷新恢复、ADMIN/STAFF 菜单、直达路由403、服务端403、退出撤销、员工停用、本人改密、503恢复、
中文日期控件、文件选择、表单验证、Table、上下文消息和 Drawer。自动验证 1024/768 宽度无页面横向溢出或导航丢失。
上传验收页只验证选择行为，不上传或生成业务图片。测试密码仅用于临时 schema。

## Open Questions

无阻止 PC-1 交付的设计问题。未实现业务的完整桌面/移动交互在对应阶段验收，当前不声称已通过完整前端交付验收。

## Implementation Checklist

本阶段的 P0/P1/P2 问题已处理；角色权限、核心认证和组件兼容性通过。数据值差异来自真实测试环境，未硬编码图稿数字。

## Follow-up Polish

后续业务页面接入时替换禁用导航，并按照同一视觉规范逐页验证；暂无需要阻断 PC-1 的 P3 项。

final result: passed
