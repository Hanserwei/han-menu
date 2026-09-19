/**
 * 经营报表模块.
 *
 * <p>管理营业额、订单、用户增长、销量排名和工作台的查询模型。通过公开查询契约或业务事件取得数据；禁止直接操作其他模块的业务表。
 *
 * <p>P6 消费公开事实并通过公开快照契约原子重建投影，员工工作台与管理员财务权限分别校验。
 */
@ApplicationModule(
    displayName = "经营报表",
    allowedDependencies = {
      "identity :: api",
      "ordering :: api",
      "ordering :: events",
      "customer :: api",
      "customer :: events",
      "payment :: api",
      "payment :: events",
      "catalog :: api",
      "shop :: api"
    })
package com.hanserwei.hanmenu.reporting;

import org.springframework.modulith.ApplicationModule;
