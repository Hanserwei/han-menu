/**
 * 顾客管理模块的公开同步契约.
 *
 * <p>用于其他模块调用顾客管理能力，后续按实际用例添加接口和不可变输入输出类型。
 *
 * <p>契约不返回聚合、数据库实体或仓储实现；接口描述业务能力，避免提供通用增删改查入口。
 */
@NamedInterface("api")
package com.hanserwei.hanmenu.customer.api;

import org.springframework.modulith.NamedInterface;
