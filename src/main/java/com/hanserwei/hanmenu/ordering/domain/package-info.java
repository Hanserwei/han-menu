/**
 * 订单管理模块的领域模型.
 *
 * <p>订单聚合通过接单、取消、派送等业务方法维护状态机，禁止通过公开状态赋值绕过校验。
 *
 * <p>领域对象负责保护不变量；本包不依赖 Spring、数据库、HTTP 或外部服务 SDK。
 */
package com.hanserwei.hanmenu.ordering.domain;
