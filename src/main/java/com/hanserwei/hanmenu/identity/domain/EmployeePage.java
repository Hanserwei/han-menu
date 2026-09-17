package com.hanserwei.hanmenu.identity.domain;

import java.util.List;

/** 员工查询结果，不向应用层泄露 ORM 实体或 Spring Data 分页类型. */
public record EmployeePage(List<EmployeeAccount> items, long totalElements) {
  /** 固定当前查询快照，避免调用方改变结果集合. */
  public EmployeePage {
    items = List.copyOf(items);
  }
}
