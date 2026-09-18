/**
 * 商品目录模块的领域模型.
 *
 * <p>菜品与套餐共享可售商品聚合，按不可变种类保护口味和组成规则；跨聚合引用由事务用例协调。
 *
 * <p>领域对象负责保护不变量；本包不依赖 Spring、数据库、HTTP 或外部服务 SDK。
 */
package com.hanserwei.hanmenu.catalog.domain;
