# 苍穹外卖业务参考清单

参考 [Danyhug/heima_sky_take_out 固定提交 f4013148](https://github.com/Danyhug/heima_sky_take_out/tree/f4013148cdd168c7af3e311dbb96b71cc532ff6b)。
已盘点 17 个控制器、70 个 HTTP 处理方法和 11 张表定义，仅用于理解业务需求。
本系统独立设计模型与协议，不继承这些路径、数据结构、默认账号或教学实现。

| 业务能力 | 参考实现 | 本系统归属 |
| --- | --- | --- |
| 员工登录、员工维护与启停用 | EmployeeController / EmployeeServiceImpl | identity |
| 分类、菜品、口味、套餐及上下架 | Category / Dish / Setmeal 相关控制器和服务 | catalog |
| 营业状态与门店信息 | ShopController 与小程序调用 | shop |
| 微信身份与顾客地址 | User / AddressBook 相关代码 | identity、customer |
| 购物车增减、查看和清空 | ShoppingCardController / ShoppingCartServiceImpl | cart |
| 下单、历史、详情、取消、再来一单、催单 | 用户及管理端 OrderController | ordering |
| 预支付、回调和退款 | OrderServiceImpl / PayNotifyController / WeChatPayUtil | payment |
| 来单与催单消息 | WebSocketServer | notification |
| 工作台、营业额、订单、顾客增长、销量与 Excel | WorkspaceService / ReportService | reporting |

需要重新建模和验证的重点：

- 原支付方法直接模拟成功，部分退款使用固定金额，不能作为真实支付能力验收依据。
- 原订单服务同时访问多个业务的 Mapper；新项目通过模块契约组织协作。
- 订单金额和地址必须在服务端校验，历史订单保存下单时的业务快照。
- 资源归属、状态机、并发版本和幂等规则需要完整验证。
- 账号密码、审计、通知鉴权和缓存持久化边界按新架构实现。
- 原客户端还调用了后端未完整暴露的门店查询、用户取消等能力；按业务需求在新模块中落实。

本次参考仓库提供的后台主要为静态构建产物，小程序也包含打包代码。它们可用于理解业务与交互，
未来客户端按本项目 API 重新设计和联调。
