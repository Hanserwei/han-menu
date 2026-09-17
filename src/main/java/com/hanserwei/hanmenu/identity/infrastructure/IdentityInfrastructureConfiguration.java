package com.hanserwei.hanmenu.identity.infrastructure;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 提供身份模块的可替换时钟，领域行为不直接读取系统时间. */
@Configuration(proxyBeanMethods = false)
class IdentityInfrastructureConfiguration {
  @Bean
  Clock identityClock() {
    return Clock.systemUTC();
  }
}
