/**
 * 门店经营模块的公开同步契约.
 *
 * <p>提供门店营业状态和联系资料的实时快照，供 App 展示与后续下单资格判断使用。
 *
 * <p>契约不返回聚合、数据库实体或仓储实现；接口描述业务能力，避免提供通用增删改查入口。
 */
@NamedInterface("api")
package com.hanserwei.hanmenu.shop.api;

import org.springframework.modulith.NamedInterface;
