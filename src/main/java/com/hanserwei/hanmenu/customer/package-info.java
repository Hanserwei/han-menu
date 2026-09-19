/**
 * 顾客管理模块.
 *
 * <p>管理顾客档案、收货地址和默认地址。读取或修改地址必须校验所属顾客；下单时对外提供地址快照。
 *
 * <p>P3 已实现当前模块。跨模块只使用明确导出的公开契约，顾客本人资源由认证主体确定归属；管理员档案和启停用通过 identity 公开契约授权及审计。
 */
@ApplicationModule(
    displayName = "顾客管理",
    allowedDependencies = {"identity :: api"})
package com.hanserwei.hanmenu.customer;

import org.springframework.modulith.ApplicationModule;
