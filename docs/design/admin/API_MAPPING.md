# 管理端页面与接口映射

核对日期：2026-09-19。路径均为真实 HTTP 路径，不是 SPA 页面路径。除登录和 storefront 外使用员工 Bearer。
A=当前管理员；S=当前管理员或普通员工。列表、写操作和字段以本项目生成的 OpenAPI 为类型来源，本文解释 UI 使用方式。

## 1. 会话与应用布局

| 位置／动作 | 方法与路径 | 输入／输出 | UI 行为 |
| --- | --- | --- | --- |
| 登录 | POST /api/v1/sessions | username/password → accessToken/tokenType/expiresAt | 保存会话后 GET /me 决定权限；不把返回体当作员工档案 |
| 角色与账号 | GET /api/v1/me | id/username/displayName/role | 控制菜单；不推测 JWT claims |
| 顶栏店名 | GET /api/v1/storefront | name/phone/address/status/version | 公开单店资料；STAFF 无须访问 /shop |
| 修改本人密码 | PUT /api/v1/me/password | currentPassword/newPassword → 204 | 没有 version 字段；成功清空认证并重新登录 |
| 退出 | DELETE /api/v1/sessions/current | 204 | 清理会话、个人查询缓存及 WebSocket |

认证失败用 RFC 9457，身份信息不使用第三方头像服务；默认头像为姓名首字或通用用户图标。

## 2. 工作台字段

S：`GET /api/v1/workspace`。

| UI | JSON 字段 | 跳转／口径 |
| --- | --- | --- |
| 经营日期 | businessDate、zone | 后端经营日；非浏览器本地时区 |
| 待接单 | awaitingAcceptance | /orders?status=PAID，不限制日期 |
| 待配送 | accepted | /orders?status=ACCEPTED |
| 配送中 | delivering | /orders?status=DELIVERING |
| 取消处理中 | cancelling | /orders?status=CANCELLING |
| 退款处理中 | refunding | /orders?status=REFUNDING |
| 今日下单／完成 | createdToday / completedToday | 分别按创建／完成时刻统计，不以列表行数充当数量 |
| 门店状态 | shopStatus | OPEN/CLOSED；管理跳转只给 A |
| 菜品在售／下架 | dishesOnSale / dishesOffSale | 仅摘要；目录暂无按状态筛选 |
| 套餐在售／下架 | mealsOnSale / mealsOffSale | 同上 |
| 投影更新时间 | projection.updatedAt | 不用当前客户端时钟替代 |

工作台待接列表另取 `GET /api/v1/management/orders?status=PAID&page=0&size=5`。两项响应独立处理失败和更新时间；
待办计数来自投影，列表来自订单事实，短暂不一致不强行改写为一致。

## 3. 订单

S：列表 `/api/v1/management/orders`；详情 `/api/v1/management/orders/{id}`。
列表参数 status/orderId/customerId/phone/from/to/page/size，时间为创建时刻左闭右开区间。

| 区域 | 返回字段 |
| --- | --- |
| 摘要表格 | items[].id/status/total/currency/createdAt；version 留作内部版本，cancelledAt 可用于取消详情提示 |
| 总量与分页 | page/size/totalElements/totalPages |
| 详情标识／状态 | id/status/version/createdAt/cancelledAt/expiresAt |
| 商品成交信息 | items[].id/productId/kind/name/unitPrice/quantity/selections/subtotal/components |
| 套餐成交组成 | components[].productId/name/quantity/selections |
| 收货快照 | address.recipientName/phone/province/city/district/detail；sourceId/sourceVersion 不作为可编辑字段 |
| 付款与履约 | lifecycle.paymentId/paidAt/acceptedAt/deliveredAt/completedAt |
| 取消与退款 | lifecycle.cancelReason/refundStatus/refundId |
| 催单 | reminderCount/lastRemindedAt |

详情没有 customerId 字段，不能在订单详情上凭空做“当前顾客档案”跳转。顾客列表可以以自己的 id 跳到订单查询。
收货人不一定等于账号昵称，也不能以订单电话倒推顾客。时间进度只使用已经存在的生命周期事实。

| 按钮 | 方法与路径 | body | 前置状态 |
| --- | --- | --- | --- |
| 接单 | POST /api/v1/management/orders/{id}/acceptance | version | PAID |
| 拒单并退款 | POST /api/v1/management/orders/{id}/rejection | version | PAID |
| 取消并退款 | POST /api/v1/management/orders/{id}/cancellation | version | PAID / ACCEPTED |
| 开始配送 | POST /api/v1/management/orders/{id}/delivery | version | ACCEPTED |
| 确认完成 | POST /api/v1/management/orders/{id}/completion | version | DELIVERING |

body 均为 `{"version":最新版本}`。成功返回最新 Detail；没有 reason、退款金额或配送员字段。
刷新只重新 GET，后台处理状态不要替换为前端倒计时推测的终态。订单下单、支付、催单、再来一单是顾客端能力，管理端不增加对应按钮。

## 4. 目录与图片（A）

| 页面／操作 | 方法与路径 | 参数或 body |
| --- | --- | --- |
| 菜品／套餐列表 | GET /api/v1/catalog/products | kind=DISH/SET_MEAL、categoryId、page、size |
| 分类选项／分类列表 | GET /api/v1/catalog/categories | 无分页；返回完整有界数组 |
| 商品详情 | GET /api/v1/catalog/products/{id} | 返回 ProductView |
| 新建商品 | POST /api/v1/catalog/products | kind、details；新建默认 OFF_SALE |
| 编辑商品 | PUT /api/v1/catalog/products/{id} | details、version；先下架 |
| 上下架 | PATCH /api/v1/catalog/products/{id}/status | status=ON_SALE/OFF_SALE、version |
| 删除商品 | DELETE /api/v1/catalog/products/{id} | query version；204 |
| 新建分类 | POST /api/v1/catalog/categories | kind、name、sortOrder |
| 分类详情 | GET /api/v1/catalog/categories/{id} | CategoryView |
| 编辑／启停分类 | PUT /api/v1/catalog/categories/{id} | name、sortOrder、enabled、version |
| 删除分类 | DELETE /api/v1/catalog/categories/{id} | query version；204 |
| 上传图片 | POST /api/v1/catalog/images | multipart file；返回图片元数据，取 id |
| 图片预览地址 | GET /api/v1/catalog/images/{id} | url/expiresAt；不是图片二进制接口 |

ProductView：id/kind/categoryId/name/description/price/currency/imageId/flavors/components/status/version/createdAt。
categoryId 用分类字典映射名称；components 只有 dishId/quantity/selections，不含菜品名称。
列表没有库存、销量、更新时间字段。列表图片按当前页 imageId 去重读取签名链接，不读下架商品的公开图片端点。

`details` 包含 categoryId/name/description/price/imageId/flavors/components。完整替换输入显式使用数组，不能把
未编辑的 flavors/components 省略或传 null。菜品 components=[]；套餐 flavors=[]。

Flavors：name/options[]/required；Components：dishId/quantity/selections。图片清除只把 imageId=null 后保存商品。
当前没有图片库列表／删除 API，不做“素材库管理”页面。分页菜品选择器不支持远程名称搜索，避免先查一页再伪装全库搜索。

## 5. 顾客、员工、门店（A）

| 资源 | 读取 | 写入／状态 |
| --- | --- | --- |
| 顾客 | GET /api/v1/management/customers，参数 phone/name/enabled/from/to/page/size；GET 同路径/{id} | PATCH 同路径/{id}/status，body enabled/version；返回新档案 |
| 员工 | GET /api/v1/employees，参数 name/page/size；GET 同路径/{id} | POST 创建；PUT /{id} 更新资料；PATCH /{id}/status，body status/version |
| 门店 | GET /api/v1/shop | PUT name/phone/address/version；PATCH /status，body status=OPEN/CLOSED、version |

顾客档案字段 id/phone/displayName/enabled/version/createdAt/updatedAt。员工字段 id/username/displayName/phone/
role/status/version/createdAt/updatedAt；员工修改资料／状态成功为 204，要重新读取最新版本。
员工 POST 输入 username/displayName/phone/password；PUT 无 password/role，只含资料及 version。
顾客没有管理端编辑资料能力；门店没有营业时间、配送费用或范围。

## 6. 资金与报表（A）

| 页面 | 路径／输入 | 核心展示 |
| --- | --- | --- |
| 支付列表／详情 | GET /api/v1/management/payments；/{id} | status=PENDING/SUCCEEDED/CLOSED；订单与顾客引用、金额、渠道交易号、时间 |
| 退款列表／详情 | GET /api/v1/management/refunds；/{id} | status=PENDING/SUCCEEDED；原支付与订单引用、金额、确认时间 |
| 经营分析 | GET /api/v1/reports/operations?from=日期&to=日期 | summary、days、projection |
| 商品销量 | GET /api/v1/reports/sales?from=日期&to=日期&limit=10 | items、projection；limit≤100 |
| 资金对账 | GET /api/v1/reports/reconciliation?from=日期&to=日期 | 确认收退款与差异计数 |
| 下载报表 | GET /api/v1/reports/export?from=日期&to=日期 | XLSX 二进制，失败仍为 Problem JSON |

资金列表均支持 status/orderId/customerId/paymentId/from/to/page/size；日期筛选使用各自创建时刻，**不是付款／退款确认时刻**。
支付详情 id/orderId/customerId/amount/currency/status/tradeNo/createdAt/expiresAt/paidAt/closeRequested/nextAttemptAt/lastFailure/version。
退款详情 id/paymentId/orderId/customerId/amount/currency/status/tradeNo/createdAt/confirmedAt/nextAttemptAt/lastFailure/version。
nextAttemptAt 放详情诊断信息，不在列表展示“系统重试倒计时”。lastFailure 翻译固定分类，未知值保留通用提示及追踪入口。

| 经营分析 UI | 对应响应字段 |
| --- | --- |
| 营业额 | operations.summary.turnover |
| 已完成订单 | operations.summary.completedOrders |
| 平均客单价 | operations.summary.averageOrderValue |
| 新增顾客 | operations.summary.newCustomers |
| 订单完成率（订单视图） | operations.summary.completionRatePercent；创建群组口径 |
| 营业额曲线 | operations.days[].date / turnover |
| 订单曲线 | days[].submittedOrders / completedOrders；分别标识创建／完成口径，不以相除计算完成率 |
| 新增／累计顾客 | days[].newCustomers / cumulativeCustomers |
| 商品销量 | sales.items[].productId/kind/name/quantity/amount |
| 统计新鲜度 | 各响应 projection.updatedAt；generation/revision 留作诊断 |
| 真实收款／退款／净收款 | reconciliation.receivedAmount/refundedAmount/netReceivedAmount |
| 差异与待处理 | missingOrders/paymentMismatches/missingPayments/refundMismatches/pendingRefunds/ordersMissingReceipts/ordersMissingRefunds |

报表 from/to 是包含首尾的经营日期；日期范围最多连续 366 天。摘要金额、完成率、平均值都使用服务端值，
不对分页明细重新汇总。图表只绘制服务器提供的数据，表格金额格式化保留两位小数，不以二进制浮点重建交易金额。

## 7. 通知、审计、维护

| 能力 | 权限 | 方法与路径 | 关键协议 |
| --- | --- | --- | --- |
| 通知补查 | S | GET /api/v1/notifications | after、limit≤100；items/nextCursor/hasMore |
| 阅读进度 | S | GET /api/v1/notifications/receipt | sequence/version/updatedAt |
| 确认阅读 | S | PUT /api/v1/notifications/receipt | sequence/version；只前移连续已处理且经用户确认的页面 |
| 长连接票据 | S | POST /api/v1/notifications/stream-tickets | ticket/expiresAt/protocol，30 秒、一次性 |
| WebSocket | S | /api/v1/notifications/stream | 子协议固定业务协议及 ticket.票据；URL 不带令牌或查询参数 |
| 投递轨迹 | A | GET /api/v1/notifications/{id}/attempts | 最多100条；id/startedAt/finishedAt/status/sent/failed/failure |
| 重投 | A | POST /api/v1/notifications/{id}/redelivery | version，仅 EXHAUSTED；返回通知新版本 |
| 安全审计 | A | GET /api/v1/management/audit-events | action/actorId/subjectId/successful/from/to/page/size |
| 投影状态 | A | GET /api/v1/reports/projection | initialized/version/generation/revision/updatedAt/rebuiltAt/计数 |
| 投影重建 | A | POST /api/v1/reports/projection/rebuild | version；同步完成后返回状态，无任务ID或百分比 |

NoticeView：id/sequence/orderId/type/occurredAt/deliveryStatus/attempts/lastFailure/version。
前端不持久化票据，不把最后实时帧序号作为阅读进度。建立连接后读取 receipt 并补查，实时帧只触发续查，幂等去重。
确认阅读与系统已拉取游标分开管理，见 UI_SPEC 第11节。

AuditEntryView：id/action/actorId/subjectId/successful/occurredAt。审计没有用户姓名、IP、设备、搜索关键字或导出接口。

## 8. 开发核对清单

- 列表字段、筛选参数、状态枚举与 OpenAPI 一一对应；没有的能力不通过当前页过滤或假数据补齐。
- 管理端、顾客端类型不能互换，使用 ManagedCustomerView、ManagedPaymentView、ManagedRefundView 等实际响应。
- REST query 参数与 SPA query 分开构建；尤其手机号的 `+` 必须由标准 URL 编码处理。
- 修改命令使用读取到的 version；密码与会话接口不套用没有的 version 字段。
- 组件负责展示与输入，查询层负责 API／缓存／取消请求；不把完整 DTO 当作编辑请求体直接回传。
- 菜品与套餐、支付与退款、顾客与员工的相似页面复用布局，不混淆字段和权限。
- 对业务 400/409 保留详情消息与 traceId；不向用户输出 SQL、SDK 请求、堆栈或认证头。
