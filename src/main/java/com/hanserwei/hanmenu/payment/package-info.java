/**
 * 支付与退款模块.
 *
 * <p>管理支付单、渠道回调、退款单及支付结果。对接外部支付渠道的代码放在基础设施层；模块使用业务引用关联订单，不依赖订单内部模型。
 *
 * <p>当前仅声明模块边界。跨模块依赖默认关闭，实现具体用例时再按公开契约逐项开放。
 */
@ApplicationModule(
    displayName = "支付与退款",
    allowedDependencies = {})
package com.hanserwei.hanmenu.payment;

import org.springframework.modulith.ApplicationModule;
