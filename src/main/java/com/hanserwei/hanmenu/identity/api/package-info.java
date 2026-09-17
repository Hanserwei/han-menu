/**
 * 账号与权限模块的公开同步契约.
 *
 * <p>提供不可变的已认证员工身份快照，供接口适配器及未来业务模块传递操作者上下文，不传递原始令牌或密码。
 *
 * <p>契约不返回聚合、数据库实体或仓储实现；接口描述业务能力，避免提供通用增删改查入口。
 */
@NamedInterface("api")
package com.hanserwei.hanmenu.identity.api;

import org.springframework.modulith.NamedInterface;
