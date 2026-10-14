# Han Menu · Archify 图表

这些图基于提交 [`95431d18ac78fd9fc45a88a6e716783b928654d4`](https://github.com/Hanserwei/han-menu/tree/95431d18ac78fd9fc45a88a6e716783b928654d4) 的实现，使用 [Archify v3.1.0](https://github.com/tt-a1i/archify/tree/v3.1.0) 生成。
每张图的 `candidate.json` 保存中文语义、源码路径与行号、固定版本及展示配置；SVG 是 README 使用的静态图，HTML 是包含查看器的独立交互图。

| 图表 | 类型 | 静态预览 | 交互文件 | 绘图源文件 |
| --- | --- | --- | --- | --- |
| 本机容器运行架构 | Architecture | [SVG](system-architecture/system-architecture.svg) | [HTML](system-architecture/system-architecture.html) | [JSON](system-architecture/candidate.json) |
| 订单驱动的模块协作 | Architecture | [SVG](module-collaboration/module-collaboration.svg) | [HTML](module-collaboration/module-collaboration.html) | [JSON](module-collaboration/candidate.json) |
| 幂等下单与支付重试 | Workflow | [SVG](order-checkout/order-checkout.svg) | [HTML](order-checkout/order-checkout.html) | [JSON](order-checkout/candidate.json) |
| 订单完整生命周期 | Lifecycle | [SVG](order-lifecycle/order-lifecycle.svg) | [HTML](order-lifecycle/order-lifecycle.html) | [JSON](order-lifecycle/candidate.json) |
| 通知与阅读恢复 | Sequence | [SVG](notification-recovery/notification-recovery.svg) | [HTML](notification-recovery/notification-recovery.html) | [JSON](notification-recovery/candidate.json) |

GitHub 文件页直接展示 SVG，HTML 文件页展示源码。下载 HTML 后用浏览器打开，即可切换主题、聚焦节点、追踪路径及导出图片，无需运行后端或安装 Archify。源码引用保留原 SSH 仓库标识，因此交互图采用 `local-only` 引用；精确路径和行号可以在相应 JSON 中查阅。

## 阅读范围

- 运行架构图对应 `infra/local-app/` 独立本机实例；生产部署需使用 HTTPS／WSS 和实际公开 Origin。
- 模块图按订单链路汇总九个业务模块，展示核心调用和可靠事件，通用鉴权与统计重建读取依赖没有全部展开。
- 下单图展示已实现的顾客 API 用例；Flutter 顾客工程尚未创建，图中的请求者表示 API 调用方。
- 生命周期图展示订单的八种实际状态。支付交易与退款交易使用各自状态；订单取消终态仍可继续记录迟到付款的补偿退款进度。
- 通知时序的纵向位置表达逻辑顺序，不表示实测耗时。启动时 HTTP 同步和连接建立并行，后台同步游标与本人已读进度独立。

## 重新生成

在仓库根执行，使用固定版本的 Archify CLI 路径：

```bash
# 将 ARCHIFY_CLI 设置为 Archify v3.1.0 技能包内 bin/archify.mjs 的实际路径。
export ARCHIFY_CLI=/path/to/archify/bin/archify.mjs

node "$ARCHIFY_CLI" finalize architecture \
  docs/diagrams/system-architecture/candidate.json \
  docs/diagrams/system-architecture/system-architecture.html \
  --repo-root "$PWD" --quality showcase \
  --out-dir /tmp/han-menu-archify-system-review-1 --json
```

其他图替换类型和文件路径即可。`finalize` 依次执行 JSON／源码证据校验、交付、静态图检查及真实浏览器检查；五张图均通过这些自动检查，并做过浏览器视觉检查。查看器运行时、浏览器和质量收据不属于业务运行依赖。

修改图表前重新核对相关源码。业务实现变化时，应更新 `meta.repository.revision` 和各节点的 `sources`，使内容与已提交版本一致；仅修改展示文字或位置时可以保留原实现版本。对同一 HTML 重新校验时使用新的 `--out-dir`，保留旧收据以便追溯。临时校验收据放在仓库外，发布目录仅保存 JSON、HTML、SVG 和查看器许可。

生成通过后打开 HTML，使用「导出 → SVG · 自动」更新 README 对应的 SVG。该格式匹配宿主浅色／深色主题；PNG、JPEG 等静态导出也可从同一菜单获取。最后检查根 README 中的预览和相对链接。

Archify 查看器遵循 [MIT License](LICENSE.archify)；导出文件内保留字体的许可说明。
