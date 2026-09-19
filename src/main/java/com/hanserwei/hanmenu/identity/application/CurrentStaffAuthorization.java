package com.hanserwei.hanmenu.identity.application;

import com.hanserwei.hanmenu.identity.api.StaffAuthorization;
import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import com.hanserwei.hanmenu.identity.domain.EmployeeRepository;
import com.hanserwei.hanmenu.identity.domain.IdentityException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 向其他模块提供当前权限校验，不接受仅凭角色字符串授予权限. */
@Service
@Transactional(readOnly = true)
class CurrentStaffAuthorization implements StaffAuthorization {
  private final EmployeeRepository employees;

  CurrentStaffAuthorization(EmployeeRepository employees) {
    this.employees = employees;
  }

  @Override
  public void requireAdministrator(StaffIdentity identity) {
    current(identity).requireAdministrator();
  }

  @Override
  public void requireStaff(StaffIdentity identity) {
    current(identity);
  }

  private com.hanserwei.hanmenu.identity.domain.EmployeeAccount current(StaffIdentity identity) {
    if (identity == null) {
      throw new IdentityException(IdentityException.Reason.INVALID_CREDENTIALS, "请先登录");
    }
    var account =
        employees
            .findById(identity.employeeId())
            .orElseThrow(
                () -> new IdentityException(IdentityException.Reason.INVALID_CREDENTIALS, "身份已失效"));
    account.requireActive(identity.securityVersion());
    return account;
  }
}
