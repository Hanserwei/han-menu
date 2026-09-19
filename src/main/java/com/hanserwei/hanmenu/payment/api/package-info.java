/**
 * 支付与退款模块的公开同步契约.
 *
 * <p>供订单登记支付、关闭和退款意图，签名参数生成与渠道调用在独立事务边界之外执行。
 *
 * <p>契约不返回聚合、数据库实体或仓储实现；接口描述业务能力，避免提供通用增删改查入口。
 */
@NamedInterface("api")
package com.hanserwei.hanmenu.payment.api;

import org.springframework.modulith.NamedInterface;
