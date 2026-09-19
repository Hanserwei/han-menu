/**
 * 购物车模块的公开同步契约.
 *
 * <p>供订单模块读取所选条目、按版本结算及按实时目录原子重新加购，所有写操作加入调用方事务。
 *
 * <p>契约不返回聚合、数据库实体或仓储实现；接口描述业务能力，避免提供通用增删改查入口。
 */
@NamedInterface("api")
package com.hanserwei.hanmenu.cart.api;

import org.springframework.modulith.NamedInterface;
