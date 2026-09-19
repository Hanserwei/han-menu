/**
 * 订单管理模块的公开同步契约.
 *
 * <p>向统计模块提供键集分页的必要订单事实，不导出地址、领域聚合或持久化实体。
 *
 * <p>契约不返回聚合、数据库实体或仓储实现；接口描述业务能力，避免提供通用增删改查入口。
 */
@NamedInterface("api")
package com.hanserwei.hanmenu.ordering.api;

import org.springframework.modulith.NamedInterface;
