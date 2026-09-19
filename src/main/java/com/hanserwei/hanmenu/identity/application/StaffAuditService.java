package com.hanserwei.hanmenu.identity.application;

import com.hanserwei.hanmenu.identity.api.StaffAudit;
import com.hanserwei.hanmenu.identity.api.StaffAuthorization;
import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import com.hanserwei.hanmenu.identity.domain.AuditTrail;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 审计通过公开契约参与业务事务，不形成跨模块表访问. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
class StaffAuditService implements StaffAudit {
  private final StaffAuthorization authorization;
  private final AuditTrail audit;

  StaffAuditService(StaffAuthorization authorization, AuditTrail audit) {
    this.authorization = authorization;
    this.audit = audit;
  }

  @Override
  public void customerStatusChanged(StaffIdentity actor, UUID customerId) {
    authorization.requireAdministrator(actor);
    audit.record(AuditTrail.Action.CHANGE_CUSTOMER_STATUS, actor.employeeId(), customerId, true);
  }
}
