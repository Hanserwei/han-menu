/**
 * 订单管理模块.
 *
 * <p>管理订单提交、接单、拒单、取消、配送、完成和催单用例。订单保存顾客、地址和商品的必要历史快照；支付交易与退款记录由支付模块维护。
 *
 * <p>P4 实现待付款订单及取消；只通过顾客、门店、目录和购物车的公开契约结算。
 */
@ApplicationModule(
    displayName = "订单管理",
    allowedDependencies = {"customer :: api", "shop :: api", "catalog :: api", "cart :: api"})
package com.hanserwei.hanmenu.ordering;

import org.springframework.modulith.ApplicationModule;
