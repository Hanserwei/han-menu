/**
 * 账号与权限模块.
 *
 * <p>管理员工账号、登录身份与访问权限。认证结果通过公开契约传递；顾客业务资料和收货地址由顾客模块维护。
 *
 * <p>P1 已实现员工登录、可撤销会话、员工管理、权限边界及安全审计；本模块不依赖其他业务模块的内部实现。
 */
@ApplicationModule(
    displayName = "账号与权限",
    allowedDependencies = {})
package com.hanserwei.hanmenu.identity;

import org.springframework.modulith.ApplicationModule;
