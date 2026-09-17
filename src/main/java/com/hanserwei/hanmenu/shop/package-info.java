/**
 * 门店经营模块.
 *
 * <p>管理营业状态以及后续确认的营业时间、配送规则。门店规则以持久化配置为准；缓存作为基础设施细节，不直接成为控制器中的业务逻辑。
 *
 * <p>当前仅声明模块边界。跨模块依赖默认关闭，实现具体用例时再按公开契约逐项开放。
 */
@ApplicationModule(
    displayName = "门店经营",
    allowedDependencies = {})
package com.hanserwei.hanmenu.shop;

import org.springframework.modulith.ApplicationModule;
