/**
 * 商品目录模块的公开同步契约.
 *
 * <p>提供实时可售商品查询和不可变目录快照，供后续购物车及下单用例重新校验业务事实。
 *
 * <p>契约不返回聚合、数据库实体或仓储实现；接口描述业务能力，避免提供通用增删改查入口。
 */
@NamedInterface("api")
package com.hanserwei.hanmenu.catalog.api;

import org.springframework.modulith.NamedInterface;
