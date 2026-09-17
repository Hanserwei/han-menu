package com.hanserwei.hanmenu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 外卖系统模块化单体的启动入口.
 *
 * <p>一个进程承载多个业务模块，由 Spring Modulith 验证模块边界。启动类只负责装配，业务规则应放在各模块的领域对象中。
 */
@EnableAsync
@SpringBootApplication
public class HanMenuApplication {

  /**
   * 启动应用，初始化数据库迁移并检查模块依赖是否合法.
   *
   * @param args Spring Boot 命令行参数
   */
  public static void main(String[] args) {
    SpringApplication.run(HanMenuApplication.class, args);
  }
}
