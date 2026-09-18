/**
 * 顾客管理模块的公开同步契约.
 *
 * <p>提供最小顾客身份快照和当前授权检查，其他模块不能访问顾客实体或私有仓储。
 *
 * <p>契约不返回聚合、数据库实体或仓储实现；接口描述业务能力，避免提供通用增删改查入口。
 */
@NamedInterface("api")
package com.hanserwei.hanmenu.customer.api;

import org.springframework.modulith.NamedInterface;
