/**
 * 消息通知模块.
 *
 * <p>管理来单提醒、催单提醒及面向管理员或顾客的消息投递。订阅已提交的业务事实；通知失败不得回滚已经完成的下单或支付事务。
 *
 * <p>P6 通过订单公开事件登记消息，通过身份公开契约鉴权和复验长连接，不读取其他模块业务表。
 */
@ApplicationModule(
    displayName = "消息通知",
    allowedDependencies = {"identity :: api", "ordering :: events"})
package com.hanserwei.hanmenu.notification;

import org.springframework.modulith.ApplicationModule;
