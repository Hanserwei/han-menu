# 参考源码接口盘点

参考提交：[`f401314`](https://github.com/Danyhug/heima_sky_take_out/tree/f4013148cdd168c7af3e311dbb96b71cc532ff6b)。
逐个读取控制器注解得到 **17 个 HTTP 控制器、70 个处理方法**；这是声明清单，尚未运行原项目验证端到端可用性。
`ANY` 表示原方法只标注 `@RequestMapping`，未限定 HTTP 方法。WebSocket 单独列在后文。
目标模块列是重构归属建议，不表示当前骨架已实现这些接口。

| 原方法 | 原服务端路径 | 目标模块 | 源码证据 |
| --- | --- | --- | --- |
| POST | `/admin/category` | `catalog` | [admin/CategoryController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/CategoryController.java#L34) |
| GET | `/admin/category/page` | `catalog` | [admin/CategoryController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/CategoryController.java#L47) |
| DELETE | `/admin/category` | `catalog` | [admin/CategoryController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/CategoryController.java#L60) |
| PUT | `/admin/category` | `catalog` | [admin/CategoryController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/CategoryController.java#L73) |
| POST | `/admin/category/status/{status}` | `catalog` | [admin/CategoryController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/CategoryController.java#L86) |
| GET | `/admin/category/list` | `catalog` | [admin/CategoryController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/CategoryController.java#L98) |
| POST | `/admin/common/upload` | `catalog` | [admin/CommonController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/CommonController.java#L30) |
| POST | `/admin/dish` | `catalog` | [admin/DishController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/DishController.java#L33) |
| GET | `/admin/dish/page` | `catalog` | [admin/DishController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/DishController.java#L41) |
| POST | `/admin/dish/status/{status}` | `catalog` | [admin/DishController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/DishController.java#L57) |
| DELETE | `/admin/dish` | `catalog` | [admin/DishController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/DishController.java#L66) |
| GET | `/admin/dish/{id}` | `catalog` | [admin/DishController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/DishController.java#L80) |
| PUT | `/admin/dish` | `catalog` | [admin/DishController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/DishController.java#L87) |
| GET | `/admin/dish/list` | `catalog` | [admin/DishController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/DishController.java#L106) |
| POST | `/admin/employee/login` | `identity` | [admin/EmployeeController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/EmployeeController.java#L43) |
| POST | `/admin/employee` | `identity` | [admin/EmployeeController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/EmployeeController.java#L68) |
| GET | `/admin/employee/page` | `identity` | [admin/EmployeeController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/EmployeeController.java#L76) |
| GET | `/admin/employee/status/{status}` | `identity` | [admin/EmployeeController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/EmployeeController.java#L84) |
| GET | `/admin/employee/{id}` | `identity` | [admin/EmployeeController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/EmployeeController.java#L92) |
| PUT | `/admin/employee` | `identity` | [admin/EmployeeController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/EmployeeController.java#L100) |
| POST | `/admin/employee/logout` | `identity` | [admin/EmployeeController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/EmployeeController.java#L113) |
| GET | `/admin/order/conditionSearch` | `ordering` | [admin/OrderController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/OrderController.java#L34) |
| GET | `/admin/order/statistics` | `ordering` | [admin/OrderController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/OrderController.java#L45) |
| GET | `/admin/order/details/{id}` | `ordering` | [admin/OrderController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/OrderController.java#L57) |
| PUT | `/admin/order/confirm` | `ordering` | [admin/OrderController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/OrderController.java#L68) |
| PUT | `/admin/order/rejection` | `ordering` | [admin/OrderController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/OrderController.java#L79) |
| PUT | `/admin/order/cancel` | `ordering` | [admin/OrderController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/OrderController.java#L90) |
| PUT | `/admin/order/delivery/{id}` | `ordering` | [admin/OrderController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/OrderController.java#L101) |
| PUT | `/admin/order/complete/{id}` | `ordering` | [admin/OrderController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/OrderController.java#L112) |
| GET | `/admin/report/turnoverStatistics` | `reporting` | [admin/ReportController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/ReportController.java#L30) |
| GET | `/admin/report/userStatistics` | `reporting` | [admin/ReportController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/ReportController.java#L45) |
| GET | `/admin/report/ordersStatistics` | `reporting` | [admin/ReportController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/ReportController.java#L60) |
| GET | `/admin/report/top10` | `reporting` | [admin/ReportController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/ReportController.java#L75) |
| GET | `/admin/report/export` | `reporting` | [admin/ReportController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/ReportController.java#L87) |
| POST | `/admin/setmeal` | `catalog` | [admin/SetmealController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/SetmealController.java#L33) |
| GET | `/admin/setmeal/page` | `catalog` | [admin/SetmealController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/SetmealController.java#L47) |
| DELETE | `/admin/setmeal` | `catalog` | [admin/SetmealController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/SetmealController.java#L60) |
| GET | `/admin/setmeal/{id}` | `catalog` | [admin/SetmealController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/SetmealController.java#L75) |
| PUT | `/admin/setmeal` | `catalog` | [admin/SetmealController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/SetmealController.java#L89) |
| POST | `/admin/setmeal/status/{status}` | `catalog` | [admin/SetmealController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/SetmealController.java#L105) |
| PUT | `/admin/shop/{status}` | `shop` | [admin/ShopController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/ShopController.java#L25) |
| GET | `/admin/shop/status` | `shop` | [admin/ShopController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/ShopController.java#L33) |
| GET | `/admin/workspace/businessData` | `reporting` | [admin/WorkSpaceController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/WorkSpaceController.java#L31) |
| GET | `/admin/workspace/overviewOrders` | `reporting` | [admin/WorkSpaceController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/WorkSpaceController.java#L47) |
| GET | `/admin/workspace/overviewDishes` | `reporting` | [admin/WorkSpaceController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/WorkSpaceController.java#L57) |
| GET | `/admin/workspace/overviewSetmeals` | `reporting` | [admin/WorkSpaceController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/admin/WorkSpaceController.java#L67) |
| GET | `/user/addressBook/list` | `customer` | [user/AddressBookController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/user/AddressBookController.java#L27) |
| POST | `/user/addressBook` | `customer` | [user/AddressBookController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/user/AddressBookController.java#L42) |
| GET | `/user/addressBook/{id}` | `customer` | [user/AddressBookController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/user/AddressBookController.java#L49) |
| PUT | `/user/addressBook` | `customer` | [user/AddressBookController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/user/AddressBookController.java#L62) |
| PUT | `/user/addressBook/default` | `customer` | [user/AddressBookController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/user/AddressBookController.java#L75) |
| DELETE | `/user/addressBook` | `customer` | [user/AddressBookController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/user/AddressBookController.java#L88) |
| GET | `/user/addressBook/default` | `customer` | [user/AddressBookController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/user/AddressBookController.java#L98) |
| GET | `/user/category/list` | `catalog` | [user/CategoryController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/user/CategoryController.java#L28) |
| GET | `/user/dish/list` | `catalog` | [user/DishController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/user/DishController.java#L36) |
| POST | `/user/order/submit` | `ordering` | [user/OrderController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/user/OrderController.java#L25) |
| PUT | `/user/order/payment` | `ordering` | [user/OrderController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/user/OrderController.java#L38) |
| GET | `/user/order/orderDetail/{id}` | `ordering` | [user/OrderController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/user/OrderController.java#L53) |
| GET | `/user/order/historyOrders` | `ordering` | [user/OrderController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/user/OrderController.java#L60) |
| POST | `/user/order/repetition/{id}` | `ordering` | [user/OrderController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/user/OrderController.java#L73) |
| GET | `/user/order/reminder/{id}` | `ordering` | [user/OrderController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/user/OrderController.java#L83) |
| ANY | `/notify/paySuccess` | `payment` | [user/PayNotifyController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/user/PayNotifyController.java#L38) |
| GET | `/user/setmeal/list` | `catalog` | [user/SetmealController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/user/SetmealController.java#L32) |
| GET | `/user/setmeal/dish/{id}` | `catalog` | [user/SetmealController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/user/SetmealController.java#L50) |
| POST | `/user/shoppingCart/add` | `cart` | [user/ShoppingCardController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/user/ShoppingCardController.java#L21) |
| GET | `/user/shoppingCart/list` | `cart` | [user/ShoppingCardController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/user/ShoppingCardController.java#L29) |
| DELETE | `/user/shoppingCart/delete` | `cart` | [user/ShoppingCardController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/user/ShoppingCardController.java#L35) |
| POST | `/user/shoppingCart/sub` | `cart` | [user/ShoppingCardController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/user/ShoppingCardController.java#L42) |
| DELETE | `/user/shoppingCart/clean` | `cart` | [user/ShoppingCardController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/user/ShoppingCardController.java#L55) |
| POST | `/user/user/login` | `identity` | [user/UserController](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/user/UserController.java#L35) |

## 前后端契约差异

- 小程序代码调用 `GET /user/shop/status` 和 `GET /user/shop/phone`，当前参考后端未发现对应控制器。
- 小程序代码调用 `PUT /user/order/cancel/{id}`；后端服务存在 `userCancelById`，但当前用户订单控制器未暴露该接口。
- 原后台通过 Nginx 将 `/api/` 转发到后端 `/admin/`，不能把浏览器路径直接当作后端路径。
- 原员工启停用使用 GET，催单也使用 GET。新接口设计应将有副作用的操作表达为写请求；历史客户端的兼容策略应在实现该功能前明确。
- 购物车同时存在 `/delete` 和 `/clean` 清空路径，迁移时保留必要别名并统一应用用例。
- WebSocket 路径为 `/ws/{sid}`，通知消息使用 `type`、`orderId`、`content`。后续在握手时校验身份，不将客户端传入的 sid 作为授权依据。

证据：[小程序请求定义](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/WeiXinApp/common/vendor.js#L20245)、
[用户订单控制器](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/controller/user/OrderController.java)、
[Nginx 配置](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/Web/nginx-1.20.2/conf/nginx.conf)、
[WebSocket 实现](https://github.com/Danyhug/heima_sky_take_out/blob/f4013148cdd168c7af3e311dbb96b71cc532ff6b/sky-server/src/main/java/com/sky/websocket/WebSocketServer.java)。

## 客户端材料范围

`Web/nginx-1.20.2/html/sky` 中是后台静态构建产物，未在 `Web` 中找到可直接构建的 `package.json`。
`WeiXinApp` 包含微信开发者工具可读的文件及打包后的 `common/vendor.js`。
因此本次以服务端和接口契约为重构重点；若要修改界面并重新构建，应补充原始前端工程，或另行规划前端重建。
