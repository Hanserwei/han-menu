/**
 * 消息通知模块的领域模型.
 *
 * <p>通知任务封装投递状态与重试策略；不同传输方式通过接口和组合扩展。
 *
 * <p>领域对象负责保护不变量；本包不依赖 Spring、数据库、HTTP 或外部服务 SDK。
 */
package com.hanserwei.hanmenu.notification.domain;
