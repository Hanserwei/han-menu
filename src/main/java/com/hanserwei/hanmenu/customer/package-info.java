/**
 * 顾客管理模块.
 *
 * <p>管理顾客档案、收货地址和默认地址。读取或修改地址必须校验所属顾客；下单时对外提供地址快照。
 *
 * <p>当前仅声明模块边界。跨模块依赖默认关闭，实现具体用例时再按公开契约逐项开放。
 */
@ApplicationModule(
    displayName = "顾客管理",
    allowedDependencies = {})
package com.hanserwei.hanmenu.customer;

import org.springframework.modulith.ApplicationModule;
