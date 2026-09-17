/**
 * 商品目录模块的领域模型.
 *
 * <p>菜品和套餐分别封装自身行为；套餐上架所需的跨聚合校验由领域服务协调。
 *
 * <p>领域对象负责保护不变量；本包不依赖 Spring、数据库、HTTP 或外部服务 SDK。
 */
package com.hanserwei.hanmenu.catalog.domain;
