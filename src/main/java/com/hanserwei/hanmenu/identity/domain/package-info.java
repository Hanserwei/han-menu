/**
 * 账号与权限模块的领域模型.
 *
 * <p>员工账号聚合封装启停用、密码变更等行为；凭证和权限策略不向其他模块泄漏。
 *
 * <p>领域对象负责保护不变量；本包不依赖 Spring、数据库、HTTP 或外部服务 SDK。
 */
package com.hanserwei.hanmenu.identity.domain;
