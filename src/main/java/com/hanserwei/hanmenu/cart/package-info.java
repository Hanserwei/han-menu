/**
 * 购物车模块.
 *
 * <p>管理顾客的待购商品、规格选择与数量。购物车不是价格真相来源；提交订单前必须重新确认商品可售性和当前价格。
 *
 * <p>P3 已实现当前模块。跨模块只使用明确导出的公开契约，顾客资源始终由认证主体确定归属。
 */
@ApplicationModule(
    displayName = "购物车",
    allowedDependencies = {"catalog :: api", "customer :: api"})
package com.hanserwei.hanmenu.cart;

import org.springframework.modulith.ApplicationModule;
