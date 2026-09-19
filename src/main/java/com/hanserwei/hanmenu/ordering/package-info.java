/**
 * 订单管理模块.
 *
 * <p>管理订单提交、接单、拒单、取消、配送、完成和催单用例。订单保存顾客、地址和商品的必要历史快照；支付交易与退款记录由支付模块维护。
 *
 * <p>P4/P5 实现结算、支付协作、取消和履约；通过公开契约调用其他模块并幂等消费支付事件。
 */
@ApplicationModule(
    displayName = "订单管理",
    allowedDependencies = {
      "customer :: api",
      "shop :: api",
      "catalog :: api",
      "cart :: api",
      "payment :: api",
      "payment :: events",
      "identity :: api"
    })
package com.hanserwei.hanmenu.ordering;

import org.springframework.modulith.ApplicationModule;
