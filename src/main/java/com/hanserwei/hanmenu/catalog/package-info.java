/**
 * 商品目录模块.
 *
 * <p>管理分类、菜品、口味、套餐和销售状态。负责商品当前信息和可售性；订单中的名称、口味、价格等历史快照由订单模块维护。
 *
 * <p>P2 已实现本模块业务；仅依赖 identity 的公开授权契约，不读取其他模块的内部实体。
 */
@ApplicationModule(
    displayName = "商品目录",
    allowedDependencies = {"identity :: api"})
package com.hanserwei.hanmenu.catalog;

import org.springframework.modulith.ApplicationModule;
