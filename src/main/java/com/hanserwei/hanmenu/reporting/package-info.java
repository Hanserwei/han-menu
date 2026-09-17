/**
 * 经营报表模块.
 *
 * <p>管理营业额、订单、用户增长、销量排名和工作台的查询模型。通过公开查询契约或业务事件取得数据；禁止直接操作其他模块的业务表。
 *
 * <p>当前仅声明模块边界。跨模块依赖默认关闭，实现具体用例时再按公开契约逐项开放。
 */
@ApplicationModule(
    displayName = "经营报表",
    allowedDependencies = {})
package com.hanserwei.hanmenu.reporting;

import org.springframework.modulith.ApplicationModule;
