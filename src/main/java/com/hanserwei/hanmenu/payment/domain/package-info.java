/**
 * 支付与退款模块的领域模型.
 *
 * <p>支付单和退款单分别封装状态迁移及幂等规则；支付网关通过接口隔离外部服务。
 *
 * <p>领域对象负责保护不变量；本包不依赖 Spring、数据库、HTTP 或外部服务 SDK。
 */
package com.hanserwei.hanmenu.payment.domain;
