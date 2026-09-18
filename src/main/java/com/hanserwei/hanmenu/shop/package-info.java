/**
 * 门店经营模块.
 *
 * <p>管理营业状态以及后续确认的营业时间、配送规则。门店规则以持久化配置为准；缓存作为基础设施细节，不直接成为控制器中的业务逻辑。
 *
 * <p>P2 已实现本模块业务；仅依赖 identity 的公开授权契约，不读取其他模块的内部实体。
 */
@ApplicationModule(
    displayName = "门店经营",
    allowedDependencies = {"identity :: api"})
package com.hanserwei.hanmenu.shop;

import org.springframework.modulith.ApplicationModule;
