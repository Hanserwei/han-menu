/**
 * 消息通知模块.
 *
 * <p>管理来单提醒、催单提醒及面向管理员或顾客的消息投递。订阅已提交的业务事实；通知失败不得回滚已经完成的下单或支付事务。
 *
 * <p>当前仅声明模块边界。跨模块依赖默认关闭，实现具体用例时再按公开契约逐项开放。
 */
@ApplicationModule(
    displayName = "消息通知",
    allowedDependencies = {})
package com.hanserwei.hanmenu.notification;

import org.springframework.modulith.ApplicationModule;
