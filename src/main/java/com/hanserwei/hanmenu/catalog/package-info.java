/**
 * 商品目录模块.
 *
 * <p>管理分类、菜品、口味、套餐和销售状态。负责商品当前信息和可售性；订单中的名称、口味、价格等历史快照由订单模块维护。
 *
 * <p>当前仅声明模块边界。跨模块依赖默认关闭，实现具体用例时再按公开契约逐项开放。
 */
@ApplicationModule(
    displayName = "商品目录",
    allowedDependencies = {})
package com.hanserwei.hanmenu.catalog;

import org.springframework.modulith.ApplicationModule;
