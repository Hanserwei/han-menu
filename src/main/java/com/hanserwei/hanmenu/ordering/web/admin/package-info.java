/**
 * 订单管理模块的员工履约资源接口.
 *
 * <p>提供员工订单查询、接单、拒单、取消、配送和完成操作，状态推进必须携带版本。
 *
 * <p>路径和响应格式遵循 /api/v1 与统一错误约定；每个写操作应校验当前操作者权限。
 */
package com.hanserwei.hanmenu.ordering.web.admin;
