package com.hanserwei.hanmenu.identity.domain;

import java.util.Optional;
import java.util.UUID;

/** 员工聚合仓储端口，领域层不依赖 ORM 类型及数据库查询语法. */
public interface EmployeeRepository {
  /** 判断初始化管理员是否已存在，重启不得重复创建或重置密码. */
  boolean hasAdministrator();

  /** 持久化新聚合，不覆盖已经存在的同名账号. */
  void add(EmployeeAccount account);

  /** 通过聚合标识加载账号. */
  Optional<EmployeeAccount> findById(UUID id);

  /** 通过规范化用户名加载账号. */
  Optional<EmployeeAccount> findByUsername(String username);

  /** 更新聚合状态，持久化实现必须阻止陈旧版本覆盖. */
  void update(EmployeeAccount account);

  /** 按显示名称的字面子串分页，页号从零开始，结果顺序由实现稳定定义. */
  EmployeePage search(String displayName, int page, int size);
}
