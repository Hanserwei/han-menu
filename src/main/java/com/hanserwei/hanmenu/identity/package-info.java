/**
 * 账号与权限模块.
 *
 * <p>管理员工账号、登录身份与访问权限。认证结果通过公开契约传递；顾客业务资料和收货地址由顾客模块维护。
 *
 * <p>当前仅声明模块边界。跨模块依赖默认关闭，实现具体用例时再按公开契约逐项开放。
 */
@ApplicationModule(
    displayName = "账号与权限",
    allowedDependencies = {})
package com.hanserwei.hanmenu.identity;

import org.springframework.modulith.ApplicationModule;
