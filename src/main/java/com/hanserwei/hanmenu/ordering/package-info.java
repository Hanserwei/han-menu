/**
 * 订单管理模块.
 *
 * <p>管理订单提交、接单、拒单、取消、配送、完成和催单用例。订单保存顾客、地址和商品的必要历史快照；支付交易与退款记录由支付模块维护。
 *
 * <p>当前仅声明模块边界。跨模块依赖默认关闭，实现具体用例时再按公开契约逐项开放。
 */
@ApplicationModule(
    displayName = "订单管理",
    allowedDependencies = {})
package com.hanserwei.hanmenu.ordering;

import org.springframework.modulith.ApplicationModule;
