/**
 * 购物车模块.
 *
 * <p>管理顾客的待购商品、规格选择与数量。购物车不是价格真相来源；提交订单前必须重新确认商品可售性和当前价格。
 *
 * <p>当前仅声明模块边界。跨模块依赖默认关闭，实现具体用例时再按公开契约逐项开放。
 */
@ApplicationModule(
    displayName = "购物车",
    allowedDependencies = {})
package com.hanserwei.hanmenu.cart;

import org.springframework.modulith.ApplicationModule;
