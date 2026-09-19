package com.hanserwei.hanmenu.payment.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 装配支付宝沙箱配置；未配置时支付用例明确失败，其他业务模块仍能启动. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AlipaySettings.class)
class AlipayConfiguration {}
