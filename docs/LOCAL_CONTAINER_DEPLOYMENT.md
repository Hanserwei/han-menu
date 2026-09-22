# 本机 Podman 上手测试部署

2026-09-22 已部署独立的前后端及中间件实例，供当前电脑实际操作。它使用独立数据库和私有图片桶，首次仅初始化管理员；菜单、门店和员工资料由使用者在管理端录入。开发用的原中间件仍由原服务管理，不参与本实例的数据清理或账号初始化。

## 访问与登录

- 管理端：<http://127.0.0.1:18080>
- 用户名：`admin`。
- 随机初始密码：保存在 `~/.local/share/han-menu/ACCESS.md`，权限0600。不要将它或runtime.env提交到Git。
- 登录后可在「我的账号」修改密码；之后以自己设置的新密码为准，ACCESS.md不会跟随改密自动更新。
- 使用上述127.0.0.1地址。通知Origin和图片签名地址采用该来源；不要随意切换为localhost或局域网IP。

服务仅供当前电脑访问。回环HTTP可作为浏览器安全上下文使用，前端不依赖证书跳过设置；若以后需要局域网访问，应另行配置HTTPS证书、公开Origin和图片地址。

## 固定的数据目录

当前用户的实际目录为 `/home/hanserwei/.local/share/han-menu`：

| 路径 | 内容 |
| --- | --- |
| `postgres/` | PostgreSQL 18实际数据目录，包含业务数据与迁移状态 |
| `redis/` | Redis AOF及持久化文件 |
| `rustfs/` | 私有商品图片与对象存储元数据 |
| `config/runtime.env` | 编排变量、随机数据库／缓存／对象存储凭证、当前镜像引用 |
| `config/backend.env` | 后端容器实际环境；包含沙箱配置，权限0600 |
| `config/redis.conf`、`config/nginx.conf`、`config/compose.yml` | 容器运行配置 |
| `logs/` | 五个容器日志，单文件限制10—20 MiB |
| `releases/` | 历次JAR、前端dist和容器构建资料；新部署还记录来源及JAR校验和 |
| `backups/` | 一致冷备份；同时包含数据库、对象、配置和发布物 |
| `ACCESS.md` | 本实例初始登录信息 |

这些是宿主机绑定目录，不是容器可写层。停止、重启或重建前后端容器不会清空业务数据。根目录0700，凭证文件0600；部分数据子目录属于容器映射UID，查看或归档可使用 `podman unshare`，不要递归改成所有人可写。

## 容器与端口

| 容器 | 用途 | 宿主机访问 |
| --- | --- | --- |
| `han-menu-app-frontend` | Nginx静态页面、API及私有图片代理 | 仅127.0.0.1:18080 |
| `han-menu-app-backend` | Java 25、Spring Boot可执行JAR | 仅127.0.0.1:18082，用于本机健康检查 |
| `han-menu-app-postgres` | 独立业务数据库 | 不发布宿主端口 |
| `han-menu-app-redis` | 独立限流与缓存 | 不发布宿主端口 |
| `han-menu-app-rustfs` | 独立私有图片存储 | 仅127.0.0.1:18090，供代理及初始化使用 |

前端Nginx使用宿主网络但只监听回环地址，其他容器使用专用Compose网络。前端通过公开访问来源的原始桶路径和Host转发签名图片，避免路径重写破坏签名；写入图片仍走后端API。对象存储控制台关闭。API文档不在本实例启用。

Java使用Temurin 25 JRE，基础镜像及Nginx固定摘要见 `infra/local-app/images.json`；数据库、Redis和RustFS使用已验证固定版本。后端以非root容器用户运行，前后端根文件系统只读，临时文件位于tmpfs。

## 日常使用

已启用用户服务 `han-menu-app.service`；本机用户已有linger，因此用户服务可在开机后启动。统一通过systemd启停整套实例：

```bash
systemctl --user status han-menu-app.service
systemctl --user stop han-menu-app.service
systemctl --user start han-menu-app.service
systemctl --user restart han-menu-app.service
```

在仓库根可查看所有容器状态或读取单个容器日志：

```bash
python3 scripts/deploy-local.py status
podman logs --tail 100 han-menu-app-backend
podman logs --tail 100 han-menu-app-frontend
curl -fsS http://127.0.0.1:18082/actuator/health
```

不要以 `podman rm -v`、删除固定目录或清空schema作为日常重启方式。脚本不提供自动清库命令；已有角色、数据库、管理员密码和对象桶会保留。

## 更新与备份

修改代码后先运行项目门禁，再构建新的前后端镜像并应用到本实例。部署操作串行加锁，禁止把同名容器悄悄指向另一个数据目录。

```bash
./scripts/verify.sh
python3 scripts/deploy-local.py deploy
```

部署读取已验证的JAR和dist，在固定目录中保存发布物，再构建两张只含运行代码的本地镜像。密码和业务数据通过外部配置及目录保留，不烘焙进镜像；更新可能短暂中断访问，页面资源变化后可刷新。

备份命令会暂时停止这五个容器，归档完整实例目录后恢复原运行状态。先结束当前正在进行的业务操作：

```bash
python3 scripts/deploy-local.py backup
```

备份位于 `backups/han-menu-时间戳.tar.gz`，权限0600，内含凭证，应按敏感备份保管。不会把历史backups重复打包。恢复前需停止服务、另行保存当前目录，并明确恢复到哪个时刻；本工具不自动覆盖现有数据。

如仓库移动位置，重新登记服务路径：

```bash
python3 scripts/deploy-local.py install-service
```

## 支付和已验证事项

新实例仅复制原.env中已有的支付宝沙箱设置，不复制开发数据库或开发管理员账号。当前已有应用及签名密钥，但尚无通知回调地址；可正常使用管理端资料和查询页面，真实顾客付款闭环仍需补齐沙箱HTTPS通知地址及顾客端／SDK联调。没有提供模拟支付成功接口或人工制造成功流水。

本次实际验证：五容器健康、管理员登录、工作台／投影／目录查询、私有图片同源签名下载、一次性票据与真实WebSocket握手、冷备份及重启后身份和凭证保留。仅执行登录与读取，未修改管理员密码或向业务表填充示例数据；图片代理探针位于独立临时对象路径，验证后已删除。
