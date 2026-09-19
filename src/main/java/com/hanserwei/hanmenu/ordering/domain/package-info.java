/**
 * 订单管理模块的领域模型.
 *
 * <p>订单聚合维护不可变成交快照，仅通过取消行为将待付款订单转为已取消；支付与履约留待后续阶段。
 *
 * <p>领域对象负责保护不变量；本包不依赖 Spring、数据库、HTTP 或外部服务 SDK。
 */
package com.hanserwei.hanmenu.ordering.domain;
