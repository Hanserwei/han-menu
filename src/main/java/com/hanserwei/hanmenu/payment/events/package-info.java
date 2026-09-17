/**
 * 支付与退款模块的公开集成事件.
 *
 * <p>用于对外表达支付与退款中已经发生的业务事实，具体事件随用例实现添加。
 *
 * <p>事件应携带稳定标识、发生时间和必要快照；监听器必须考虑重复投递及契约演进。
 */
@NamedInterface("events")
package com.hanserwei.hanmenu.payment.events;

import org.springframework.modulith.NamedInterface;
