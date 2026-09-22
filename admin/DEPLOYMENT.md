# 管理端构建、部署与回滚

适用单店、单进程后端：Nginx提供管理端静态文件并终止TLS，同机回环代理到Java后端。前端独立发布，数据库演进仍由后端Flyway负责。本文的公网域名、证书路径与部署目录为示例，执行时替换为实际值。

## 构建与验收

构建机使用仓库固定的Node 24.21.0、pnpm 12.4.2；后端使用JDK 25。生产Nginx版本在 `deploy/runtime.json` 固定为1.30.5-alpine及镜像摘要。本地验收还需要OpenSSL和运行中的Podman或Docker；Linux host网络用于连接回环测试后端。设置 `ADMIN_CONTAINER_RUNTIME=docker` 可明确选择Docker，CI使用此方式。

在仓库根运行：

```bash
pnpm --dir admin install --frozen-lockfile
pnpm --dir admin exec playwright install chromium
./scripts/verify.sh
pnpm --dir admin release
```

完整门禁保留后端与现有45项开发服务浏览器回归，另启独立测试schema，运行生产dist、真实Nginx、TLS/WSS和桌面验收。生产验收的回环端口为15174、15175，后端为18081；证书临时生成，只有该测试浏览器忽略其自签名错误。测试不发布公网、不使用开发管理员改密或造数据。

`pnpm release`再次检查前端并生成 `.local/releases/*.tar.gz` 及外层SHA256。包内只有：

- `dist/`：部署的静态产物，不含开发组件、源码映射或测试账号。
- `deploy/`：完整Nginx模板、配置生成器及固定运行镜像说明。
- `DEPLOYMENT.md`：本文。
- `release.json`：来源提交、工作区是否有未提交内容、构建时刻、Node版本。
- `SHA256SUMS`：以上内容逐文件校验值。

正式版本宜来自已提交且CI通过的提交；有未提交修改的本地交付包会明确标记dirty，不把HEAD误当成全部产物的来源。前端环境变量只用于公开开发代理配置；发布包不读取或包含后端.env。

## 后端与来源设置

后端继续用已有受保护配置启动JAR，绑定 `127.0.0.1:8080`，不要让客户端直接绕过Nginx访问它。在原有环境之外设置：

```bash
SERVER_ADDRESS=127.0.0.1
SERVER_FORWARD_HEADERS_STRATEGY=native
SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES='127\.0\.0\.1|::1|0:0:0:0:0:0:0:1'
SERVER_TOMCAT_REDIRECT_CONTEXT_ROOT=false
NOTIFICATION_ALLOWED_ORIGINS=https://admin.example.com
```

只信任实际的同机代理。模板覆盖转发IP、Host、端口和协议并去掉客户端Forwarded头，登录IP限流可使用真实来源。不要把trusted代理配置留空，也不要在后端端口公开时信任任意转发头。若改变为跨主机部署，需调整绑定、防火墙和明确可信代理地址，重新验收。

生产使用浏览器信任的证书和HTTPS/WSS。商品图片的 `RUSTFS_PUBLIC_ENDPOINT` 同样必须为浏览器可访问的HTTPS地址，并指向同一对象存储；签名生成地址与浏览器访问地址应一致，不通过字符串替换改写预签名URL。付款通知HTTPS地址仍由后端沙箱配置管理。

## 校验和发布静态文件

建议每次解包到新的 `/srv/han-menu/releases/<release-id>`，文件只读提供给Nginx。以下在部署机执行，目录和权限由部署账号准备：

```bash
# 在交付包所在目录验证外层文件，再解包到一个新的版本目录。
release_id=2026-09-22-r1
release_file=han-menu-admin-REPLACE_WITH_BUILD_ID.tar.gz
sha256sum -c "$release_file.sha256"
mkdir -p "/srv/han-menu/releases/$release_id"
tar -xzf "$release_file" -C "/srv/han-menu/releases/$release_id"
cd "/srv/han-menu/releases/$release_id"
sha256sum -c SHA256SUMS
```

静态根目录固定为 `/srv/han-menu/www`。先复制带内容哈希的assets，最后原子切换HTML入口。保留旧assets，使已打开的旧版本页面继续按旧入口加载分包；不要用带删除选项的同步覆盖整个目录。

```bash
mkdir -p /srv/han-menu/www/assets
cp -a dist/assets/. /srv/han-menu/www/assets/
cp dist/index.html /srv/han-menu/www/index.html.next
mv -f /srv/han-menu/www/index.html.next /srv/han-menu/www/index.html
```

已交付产物只有index.html和assets。若将来添加其他根静态资源，应更新发布步骤和产物验证。保留发布前版本目录及其校验文件，旧assets不在本次流程自动清理。

## 生成并验证Nginx配置

配置是完整nginx.conf，独立站点使用；已有共享Nginx应由部署人员把相关http/server设置并入现有配置，不覆盖其他站点。

```bash
node deploy/render-nginx.mjs \
  --listen 0.0.0.0:443 \
  --server-name admin.example.com \
  --root /srv/han-menu/www \
  --backend 127.0.0.1:8080 \
  --certificate /etc/han-menu/tls/fullchain.pem \
  --key /etc/han-menu/tls/privkey.pem \
  --output /etc/nginx/han-menu.conf
nginx -t -c /etc/nginx/han-menu.conf
# 首次启动；日常由本机服务管理器启动相同配置。
nginx -c /etc/nginx/han-menu.conf
# 后续仅配置或证书变动需要重载，发布静态入口无需重启后端。
nginx -c /etc/nginx/han-menu.conf -s reload
```

生成器拒绝公开HTTP绑定、缺失配对证书、非法路径／主机／端口和配置注入。模板默认仅开放HTTPS；如要提供80端口跳转，应另设使用固定真实域名的HTTPS重定向server，不在HTTP提供管理页面。

模板行为：

- SPA深链接回退index.html，入口 `Cache-Control: no-store`。
- `/assets/`成功响应长期immutable缓存；资源缺失返回404，不回退HTML。
- `/api/`保持原始路径、Bearer、Origin和WebSocket子协议，代理HTTP/1.1升级。
- 后端401、403、409、429、503及XLSX原样返回；代理自身无法连接上游时返回503 Problem JSON。
- 重建代理读写超时210秒，覆盖前端195秒和后端180秒事务上限；`proxy_next_upstream off`，不自动重发业务命令。
- 上传请求上限6MiB，匹配后端总请求上限；业务图片仍受5MiB限制。
- 访问日志不含查询串、认证头或正文；普通上游错误通过固定状态与耗时诊断，避免默认error日志写出敏感完整请求行。
- 源码、隐藏文件、内部健康检查和接口文档不从该站点对外提供。后端运维探测在受控回环执行。

## 发布后核查与恢复

验证登录、刷新深链接、ADMIN/STAFF菜单隔离、来单WSS和HTTP补查、图片预览、一次版本化写入、报表下载及退出撤销。现场业务操作使用明确的验收账号和获准的数据，不拿开发管理员做停用／改密测试。

分包因网络或发布切换加载失败时，页面提供手动重新加载按钮；主入口脚本失败时原生HTML也有刷新链接。不会自动刷新草稿。网络离线时查询可能暂停，连接状态显示断网；恢复在线后继续补查。409或未知写结果仍需先读取服务端状态再确认，重建不能根据本地超时自动重发。

回滚只恢复上一版静态入口，旧assets已保留，不回滚数据库、不修改订单或资金：

```bash
previous_release_id=2026-09-21-r1
cp "/srv/han-menu/releases/$previous_release_id/dist/index.html" /srv/han-menu/www/index.html.next
mv -f /srv/han-menu/www/index.html.next /srv/han-menu/www/index.html
```

回滚前确认旧前端与当前后端契约兼容；本轮后端契约未变。回滚后核查上述登录和关键读写流程。密钥、日志、后端数据与构建包分别管理，不把解包目录以外的配置复制到静态根目录。

参考：[Nginx WebSocket代理](https://nginx.org/en/docs/http/websocket.html)、[代理指令](https://nginx.org/en/docs/http/ngx_http_proxy_module.html)、[Spring Boot转发头与可信代理](https://docs.spring.io/spring-boot/how-to/webserver.html)。
