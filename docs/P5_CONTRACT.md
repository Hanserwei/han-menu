# P5：支付宝沙箱支付、退款与订单履约

## 实施范围

P5 提供支付宝沙箱 App 支付参数、支付状态查询与主动查单、RSA2 异步通知验签、未支付关单、
全额退款与退款确认、订单超时，以及员工接单／拒单／取消／配送／完成。支付结果来自真实渠道事实，
不接受 Flutter 返回的支付成功，也没有返回模拟成功的生产接口。

Flutter 工程、Android/iOS SDK 集成和真机 App 联调属于 P7。本阶段以浏览器沙箱收银台触发真实
沙箱交易，验证后端资金生命周期；App 参数使用真实官方 SDK 签名并通过 RSA2 自动化测试。
这不等同于已完成 Flutter App SDK 的真机支付验收。

单店、CNY、无优惠／配送费／库存预占沿用 P4。退款仅用于授权取消或迟到付款补偿，金额严格等于
原支付全额；没有任意金额或部分退款接口。配送不接入外部骑手平台。

## 模块与依赖

ordering 依赖 payment 的公开 api/events，以及 identity 的公开员工权限契约。payment 只依赖
customer 的公开授权 API，通过 UUID businessRef 关联订单，不导入 ordering 类型、不查询订单表。
领域模型保持纯 Java；JPA 与 SDK 对象不进入领域或 HTTP 响应。

采用官方 `com.alipay.sdk:alipay-sdk-java:4.40.996.ALL` v2 协议 SDK，版本已核对官方源码及 Maven
Central。仅使用 JSON、公钥 RSA2 和默认 JDK HTTP 实现，排除未使用的 dom4j、BouncyCastle 和
OkHttp 依赖。SDK 原始请求／错误日志关闭，避免签名参数和渠道报文进入日志。

网关固定校验为 `https://openapi-sandbox.dl.alipaydev.com/gateway.do`，生产网关配置会被拒绝。
连接超时 3 秒，读取超时 8 秒。接口超时、非预期响应或验签失败不能被当作付款、关单或退款成功。

## 顾客与渠道 API

顾客沿用独立 Bearer 安全链。新接口严格拒绝未定义字段，不接收 amount、sellerId、tradeNo、
支付成功标记或任意目标状态。所有 UUID 为字符串、时刻为 UTC ISO-8601、金额为精确十进制元。

| 方法与路径 | 请求与权限 | 响应 |
| --- | --- | --- |
| POST `/api/v1/orders/{id}/payments` | 顾客；Idempotency-Key；`{"version":1}` 为订单版本 | 首次 201，重放 200；Location、Idempotency-Replayed，支付参数 DTO |
| GET `/api/v1/payments/{id}` | 所属顾客 | 200 已保存的支付事实 |
| POST `/api/v1/payments/{id}/refresh` | 所属顾客；`{"version":0}` 为支付版本 | 200 当前状态；有处理窗口时不重复请求渠道 |
| GET `/api/v1/refunds/{id}` | 所属顾客 | 200 退款状态与金额 |
| POST `/api/v1/payment-notifications/alipay` | 支付宝表单及 RSA2 签名；不用顾客 Bearer | 支付事实及事件登记提交后返回文本 `success`；失败返回非成功状态与 `failure` |

创建支付响应包含 id/status/amount/currency/expiresAt/version/channel/invocation/orderString。
channel 固定 `ALIPAY_SANDBOX`，invocation 为 `APP`。orderString 是不透明签名参数，仅待付款、
未申请关闭且未过期时返回；已成功、关闭或正在取消时为 null，客户端应展示当前订单／支付状态。
App 参数使用 `alipay.trade.app.pay`、`QUICK_MSECURITY_PAY`、服务端金额和固定 time_expire。

支付查询包含 id、orderId、status、amount、currency、expiresAt、paidAt、closeRequested、version、
refundId、lastFailure。退款查询包含 id、paymentId、orderId、amount、currency、status、createdAt、
confirmedAt、version、lastFailure。lastFailure 只使用固定 `CHANNEL_UNAVAILABLE`，不返回渠道原文。

创建支付幂等键作用域为顾客与创建支付用例，格式沿用 P4 的 1—128 位 ASCII 字母／数字及 `. _ : -`。
相同键必须对应相同订单标识、订单版本、金额和截止时间；同键改变请求返回 409。每个订单最多
一个支付意图，不允许换键生成第二个渠道交易。重放使用原始订单版本，即使绑定支付后订单版本已增加，
仍返回同一个支付标识。所有新意图须通过当前订单版本检查，并与订单支付引用同事务提交。

创建预检基础沙箱配置；生成签名参数若失败，已登记意图仍可用原键恢复，不延长支付时间或新建交易。
原始支付参数和私钥均不保存进业务表，DTO 的字符串表示会隐藏签名参数。

## 员工履约 API

当前有效的 ADMIN 或 STAFF 可处理订单，禁用／改密后的旧员工会话不能操作；顾客令牌不能进入
员工路径。每个动作都携带 `{"version":当前订单版本}`，不支持直接赋值状态。

| 方法与路径 | 业务规则 |
| --- | --- |
| GET `/api/v1/management/orders?status=PAID&page=0&size=20` | status 可省略；page 0—10000，size 1—50；返回不含地址的摘要投影 |
| GET `/api/v1/management/orders/{id}` | 读取履约所需的完整订单及收货快照 |
| POST `/api/v1/management/orders/{id}/acceptance` | PAID → ACCEPTED |
| POST `/api/v1/management/orders/{id}/rejection` | PAID → REFUNDING，发起全额退款 |
| POST `/api/v1/management/orders/{id}/cancellation` | PAID 或 ACCEPTED → REFUNDING |
| POST `/api/v1/management/orders/{id}/delivery` | ACCEPTED → DELIVERING |
| POST `/api/v1/management/orders/{id}/completion` | DELIVERING → COMPLETED |

所有成功操作返回 200 及最新详情／版本。旧版本、跳步、重复接单、重复完成或配送后取消均返回
409；没有为通过测试放宽员工或架构边界。

## 订单、支付与退款状态

订单主流程：`UNPAID → PAID → ACCEPTED → DELIVERING → COMPLETED`。

P4 的顾客取消接口 `/api/v1/orders/{id}/cancellation` 扩展为接单前取消：

- UNPAID 且没有支付意图：直接 CANCELLED。
- UNPAID 且已有支付意图：CANCELLING，等待真实关单结果；若实际已付款，则转入退款。
- PAID：REFUNDING；查询确认退款后才变为 CANCELLED。
- ACCEPTED、DELIVERING、COMPLETED：顾客不能取消；商家只能在开始配送前取消。

支付状态为 PENDING、SUCCEEDED、CLOSED。SUCCEEDED 不会被延迟的 PENDING／CLOSED 通知覆盖。
若先得到 CLOSED，后来才收到真实成功，仍记录成功付款事实并通知订单进行补偿。

订单详情新增 expiresAt 与 lifecycle，后者包含 paymentId、paidAt、acceptedAt、deliveredAt、
completedAt、cancelReason、refundStatus、refundId。refundStatus 为 NONE／PENDING／SUCCEEDED。
已取消订单的迟到付款只改变独立退款状态，主订单保持 CANCELLED，不能恢复接单或配送。
退款结果先于付款事件到达也不会重新激活订单或再建退款。

取消原因固定为 CUSTOMER、TIMEOUT、MERCHANT_REJECTED、MERCHANT_CANCELLED、PAYMENT_CLOSED，
不保存自由文本或请求体。退款号在收到完成事件后进入订单详情；处理中退款号可从支付查询取得。

## 超时、关单与恢复

支付窗口固定为订单创建后十五分钟，支付意图创建和参数重试都不能延长。SDK 的 time_expire
转换为支付宝的 Asia/Shanghai 格式；应用与数据库仍使用 UTC。

超时任务每十秒最多读取一百个到期 UNPAID 订单，逐个在独立事务中锁定订单并保存取消意图。
已确认付款的订单不会被过期扫描覆盖。付款消费者与取消／接单／配送均使用同一订单行锁，版本
检查保护客户端并发操作，不能同时接受互相冲突的动作。

关闭意图不会把“渠道没有查到交易”立即当作 CLOSED：有效 App 参数可能尚未在渠道创建交易。
只有真实 TRADE_CLOSED，或固定支付截止时间过后再加两分钟且查询仍明确不存在，才确认关闭。
关单请求后再次查询事实；支付成功竞争进入全额退款。关闭支付单在截止后一天内继续每十分钟复查，
之后仍可接受迟到的验签成功通知并补偿。

支付／退款任务的 nextAttemptAt 持久化。领取时在短行锁事务中占用六十秒处理窗口，提交后释放
数据库锁再请求渠道。进程退出后窗口到期即可重新领取；默认故障／未确认结果三十秒后重试。
重复关单／退款请求使用原交易号和退款号，结果应用在行锁下幂等处理，不依赖进程内锁。

支付查询、关单、退款与参数生成用例拒绝被数据库事务包裹；Spring `Propagation.NEVER` 与
自动化测试共同约束，外部 HTTP 不占用订单或支付数据库长事务。

退款先查询原退款号；若没有成功记录才发起退款，再次查询确认。官方接口可能返回 `10000` 但
没有退款业务字段，这表示尚未找到成功退款，不是验签失败或已经退款；已有成功记录必须核对
原交易号、退款号、原金额和全额退款金额。退款受理、网络响应丢失及等待处理都继续显示 PENDING。

## 可靠事件与安全

支付状态和 PaymentResult、退款状态和 RefundResult 通过 Modulith JPA 登记共同提交／回滚。
ordering 使用 ApplicationModuleListener，在独立事务中消费并按支付／退款标识与聚合状态幂等。
失败事件保留，默认每分钟重投至少一分钟前的未完成事件，批次 100、最多同时处理 10 项；启动
也可重投未完成登记。事件只含内部标识、金额和状态／时间，没有地址、令牌、签名或个人资料。

通知独立安全链只允许精确 POST 路径。控制器拒绝重复参数和超大字段；适配器核对 RSA2、app_id、
seller_id、签名、支付标识、渠道交易号、金额及付款时间，再与数据库已有支付单匹配。未知或
不匹配支付不能创建成功记录。主动查询与退款成功响应由官方 SDK 验签后再核对业务引用及金额。

JSON API 沿用 RFC 9457、code、traceId。PAYMENT_INVALID_INPUT／INVALID_NOTIFICATION 为 400；
NOT_FOUND 为 404；VERSION_CONFLICT／CONFLICT 为 409；UNAVAILABLE 为 503。渠道回调使用支付宝
要求的文本协议，不伪装为普通顾客 JSON 接口。

## 配置与沙箱手动验收

真实凭证只放被忽略、权限为 600 的 `.env` 或环境变量：

- ALIPAY_APP_ID、ALIPAY_SELLER_ID：网页／移动应用及绑定商家 PID。
- ALIPAY_PRIVATE_KEY：Java PKCS8 应用私钥；ALIPAY_PUBLIC_KEY：支付宝公钥。
- ALIPAY_GATEWAY：只允许上述沙箱 HTTPS 网关。
- ALIPAY_NOTIFY_URL：可从公网访问的 HTTPS 通知地址。
- PAYMENT_SCHEDULING_ENABLED：默认 true；自动化测试显式关闭后台定时扫描。
- 手动验收另需 ALIPAY_SANDBOX_BUYER_ID，浏览器付款使用控制台提供的沙箱买家及支付密码。

`SandboxAcceptance` 是测试源码中的显式手动入口，不在可执行生产 JAR 中。它运行真实应用服务和
SDK，使用独立测试库的随机 schema、固定 0.01 元模拟交易，并在停止后删除自己的 schema。
本机控制端口只绑定 127.0.0.1:8186，业务测试应用为 8185。

```bash
# 先通过完整质量门禁。
./scripts/verify.sh
# 转发器只放行通知 POST；将你选择的临时 HTTPS 隧道指向 127.0.0.1:8191。
python3 scripts/alipay-notify-relay.py
# 将得到的 HTTPS 地址加通知路径写入 .env 的 ALIPAY_NOTIFY_URL，再启动验收。
ALIPAY_SANDBOX_ACCEPTANCE=true ./scripts/alipay-sandbox-acceptance.sh
```

浏览器打开 `http://127.0.0.1:8186/checkout`。这个**仅在测试工具内**的网页支付入口用于没有 Flutter
设备时触发沙箱交易，同一后端支付单仍经过真实验签通知、查单与退款能力；生产 API 不增加网页支付入口。
读取 `GET /status` 核对当前订单、支付及退款事实。工具的 POST 控制操作必须带
`X-Han-Menu-Probe: local` 且不能携带 Origin：`/create-channel` 创建真实未付款渠道交易用于关单验收；
`/cancel` 调用订单取消；`/new` 建立下一笔测试；`/stop` 正常关闭并清理随机 schema。

临时隧道只应指向通知转发器，不能把整个管理／顾客应用暴露出去；它不是长期部署的回调地址。
验收结束后关闭验收进程、转发器和隧道，下次支付联调须重新配置有效 ALIPAY_NOTIFY_URL。

## 迁移和验证

新增 V6，扩展 ordering_order 生命周期字段及约束，新建 payment_intent、payment_refund。
订单支付引用与支付业务引用无跨模块外键；唯一全额退款的外键仅指向 payment 模块内部支付表。
每订单唯一支付、每顾客唯一支付幂等键、每支付唯一退款和唯一渠道交易号由数据库兜底。
历史订单、地址、价格快照不重建、不清空，V1—V5 不修改。

自动化验证覆盖 RSA2 签名及篡改、主体／金额／交易引用校验、不可变截止时间、关闭与付款乱序、
关单结果不明、退款响应丢失和查询恢复、首次空退款查询、重复通知、事件回滚／失败重投、并发接单
与取消、过期竞争、员工停用、顾客越权、HTTP 字段／版本和全额退款。

官方依据：[Java SDK 源码](https://github.com/alipay/alipay-sdk-java-all)、
[SDK 发布配置](https://raw.githubusercontent.com/alipay/alipay-sdk-java-all/master/v2/pom.xml)、
[App 支付文档](https://opendocs.alipay.com/open/00dn7d?pathHash=f5e7ce65)。

## 2026-09-19 验收记录

- P4 已独立提交为 `4142888`，提交钩子的完整门禁通过。
- 最终 `./mvnw spotless:apply`、`./scripts/verify.sh` 均成功：119 项测试，零失败、零错误、零跳过；
  Checkstyle 零违规，Modulith／ArchUnit 模块约束通过，真实 PostgreSQL／Redis／RustFS 回归通过。
- 真实沙箱验证：签名查单返回明确不存在；创建待付款渠道交易、主动关单、订单取消闭环成功。
- 使用沙箱买家余额完成 0.01 元模拟付款，真实 HTTPS 异步通知经 RSA2 验签后返回 success，
  本地支付 SUCCEEDED、订单 PAID；主动查单也确认 TRADE_SUCCESS、0.01 元及付款时间。
- 实际渠道首次空退款查询暴露的兼容处理已修复并补充回归。使用修复版本再次完成新交易，
  后端自动登记／执行／查询确认全额退款，最终订单 CANCELLED、退款 SUCCEEDED，无手动修改业务状态。
- 验收随机 schema 已由工具正常清理，临时转发器、隧道与收银台已关闭。当前沙箱凭证仍在被忽略的
  `.env`（权限 600）；过期的临时 ALIPAY_NOTIFY_URL 已移除。再次支付联调前须配置有效 HTTPS 通知地址。
- 本次未向开发库应用 V6、未清空开发数据、未接触生产资金；没有创建 Flutter 工程或声称已完成真机 SDK 联调。
