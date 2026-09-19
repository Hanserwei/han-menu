/**
 * 支付与退款模块.
 *
 * <p>管理支付单、渠道回调、退款单及支付结果。对接外部支付渠道的代码放在基础设施层；模块使用业务引用关联订单，不依赖订单内部模型。
 *
 * <p>P5 实现支付宝沙箱支付、关单、全额退款及可靠结果事件；只依赖顾客公开授权契约。
 */
@ApplicationModule(
    displayName = "支付与退款",
    allowedDependencies = {"customer :: api"})
package com.hanserwei.hanmenu.payment;

import org.springframework.modulith.ApplicationModule;
