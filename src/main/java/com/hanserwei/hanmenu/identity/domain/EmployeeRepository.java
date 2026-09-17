package com.hanserwei.hanmenu.identity.domain;

import java.util.List;
import java.util.Optional;

/** 员工聚合的持久化端口，业务不依赖 JDBC 的存取形式. */
public interface EmployeeRepository {
  /** 分配数据库生成的稳定标识，失败的事务允许留下序号间隔. */
  long nextIdentity();

  /** 判断管理员是否已经初始化，避免改名或重启后重复创建管理员. */
  boolean hasAdministrator();

  /** 持久化新账号，不覆盖已有同名账号. */
  void add(EmployeeAccount account);

  /** 根据标识读取账号. */
  Optional<EmployeeAccount> findById(long id);

  /** 根据规范化的用户名读取账号. */
  Optional<EmployeeAccount> findByUsername(String username);

  /** 按加载版本条件更新，禁止覆盖并发修改. */
  void update(EmployeeAccount account);

  /** 返回按标识排序的分页数据，姓名查询按字面子串匹配. */
  List<EmployeeAccount> page(String name, int offset, int limit);

  /** 返回与分页条件一致的总记录数. */
  long count(String name);
}
