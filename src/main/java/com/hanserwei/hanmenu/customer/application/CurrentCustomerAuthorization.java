package com.hanserwei.hanmenu.customer.application;

import com.hanserwei.hanmenu.customer.api.CustomerAuthorization;
import com.hanserwei.hanmenu.customer.api.CustomerIdentity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 顾客公开授权契约的实现，业务模块不需要访问账号实体. */
@Service
@Transactional(readOnly = true)
class CurrentCustomerAuthorization implements CustomerAuthorization {
  private final CustomerAuthentication authentication;

  CurrentCustomerAuthorization(CustomerAuthentication authentication) {
    this.authentication = authentication;
  }

  @Override
  public void requireActive(CustomerIdentity identity) {
    authentication.current(identity);
  }
}
