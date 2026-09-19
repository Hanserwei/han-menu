/**
 * 订单管理模块的领域模型.
 *
 * <p>订单聚合维护不可变成交快照，通过明确付款、取消、退款确认和履约行为维护状态机，已取消订单不因迟到付款恢复履约。
 *
 * <p>领域对象负责保护不变量；本包不依赖 Spring、数据库、HTTP 或外部服务 SDK。
 */
package com.hanserwei.hanmenu.ordering.domain;
