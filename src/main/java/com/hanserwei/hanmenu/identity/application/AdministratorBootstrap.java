package com.hanserwei.hanmenu.identity.application;

import com.hanserwei.hanmenu.identity.domain.NewPassword;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 按显式配置初始化管理员，已有账号不重置密码，密码不写入迁移脚本或启动日志. */
@Component
@ConditionalOnProperty(name = "han-menu.identity.bootstrap-enabled", havingValue = "true")
class AdministratorBootstrap implements ApplicationRunner {
  private final EmployeeAdministration administration;
  private final String username;
  private final String password;

  AdministratorBootstrap(
      EmployeeAdministration administration,
      @Value("${han-menu.identity.bootstrap-username}") String username,
      @Value("${han-menu.identity.bootstrap-password}") String password) {
    this.administration = administration;
    this.username = username;
    this.password = password;
  }

  @Override
  public void run(ApplicationArguments arguments) {
    administration.bootstrap(username, new NewPassword(password));
  }
}
