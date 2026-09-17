package com.hanserwei.hanmenu.identity.application;

import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import com.hanserwei.hanmenu.identity.domain.AuditTrail;
import com.hanserwei.hanmenu.identity.domain.EmployeeAccount;
import com.hanserwei.hanmenu.identity.domain.EmployeePage;
import com.hanserwei.hanmenu.identity.domain.EmployeeProfile;
import com.hanserwei.hanmenu.identity.domain.EmployeeRepository;
import com.hanserwei.hanmenu.identity.domain.IdentityException;
import com.hanserwei.hanmenu.identity.domain.NewPassword;
import com.hanserwei.hanmenu.identity.domain.PasswordHasher;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 员工管理应用服务，负责事务与用例编排，业务规则委托给聚合. */
@Service
@Transactional
public class EmployeeAdministration {
  private final EmployeeRepository employees;
  private final PasswordHasher passwords;
  private final AuditTrail audit;
  private final Clock clock;

  /** 通过端口组合持久化、密码与审计能力，不依赖具体 ORM 实现. */
  public EmployeeAdministration(
      EmployeeRepository employees, PasswordHasher passwords, AuditTrail audit, Clock clock) {
    this.employees = employees;
    this.passwords = passwords;
    this.audit = audit;
    this.clock = clock;
  }

  /** 首次部署创建管理员；重新启动不改变已存在管理员的凭证. */
  public void bootstrap(String username, NewPassword password) {
    if (employees.hasAdministrator()) {
      return;
    }
    var account =
        EmployeeAccount.create(
            UUID.randomUUID(),
            new EmployeeProfile(username, "系统管理员", ""),
            passwords.encode(password),
            EmployeeAccount.Role.ADMIN,
            clock.instant());
    employees.add(account);
    audit.record(AuditTrail.Action.BOOTSTRAP, account.id(), account.id(), true);
  }

  /** 创建普通员工，初始密码必须明确提供，接口不能授予管理员角色. */
  public EmployeeAccount create(
      StaffIdentity actor, EmployeeProfile profile, NewPassword password) {
    administrator(actor);
    var account =
        EmployeeAccount.create(
            UUID.randomUUID(),
            profile,
            passwords.encode(password),
            EmployeeAccount.Role.STAFF,
            clock.instant());
    employees.add(account);
    audit.record(AuditTrail.Action.CREATE_EMPLOYEE, actor.employeeId(), account.id(), true);
    return account;
  }

  /** 使用调用者提供的版本更新资料，陈旧编辑不得覆盖已提交的数据. */
  public void revise(StaffIdentity actor, UUID id, EmployeeProfile profile, long version) {
    administrator(actor);
    var account = employee(id);
    account.requireVersion(version);
    account.reviseProfile(profile, clock.instant());
    employees.update(account);
    audit.record(AuditTrail.Action.UPDATE_EMPLOYEE, actor.employeeId(), id, true);
  }

  /** 启停用账号，同时维护聚合的会话撤销版本. */
  public void changeStatus(StaffIdentity actor, UUID id, boolean enabled, long version) {
    administrator(actor);
    var account = employee(id);
    account.requireVersion(version);
    account.changeEnabled(enabled, clock.instant());
    employees.update(account);
    audit.record(AuditTrail.Action.CHANGE_STATUS, actor.employeeId(), id, true);
  }

  /** 员工修改本人密码，旧密码校验成功后撤销该账号全部既有会话. */
  public void changePassword(StaffIdentity actor, String oldPassword, NewPassword replacement) {
    var account = employee(actor.employeeId());
    account.requireActive(actor.securityVersion());
    if (!passwords.matches(oldPassword, account.passwordHash())) {
      throw new IdentityException(IdentityException.Reason.INVALID_CREDENTIALS, "原密码不正确");
    }
    account.changePassword(passwords.encode(replacement), clock.instant());
    employees.update(account);
    audit.record(AuditTrail.Action.CHANGE_PASSWORD, actor.employeeId(), account.id(), true);
  }

  /** 管理员读取员工聚合，接口层必须转换为明确的响应模型. */
  @Transactional(readOnly = true)
  public EmployeeAccount get(StaffIdentity actor, UUID id) {
    administrator(actor);
    return employee(id);
  }

  /** 返回零基页号查询的稳定快照，分页对象不使用 Spring Data 的接口类型. */
  @Transactional(readOnly = true)
  public EmployeePage search(StaffIdentity actor, String name, int page, int size) {
    administrator(actor);
    if (page < 0 || page > 10000 || size < 1 || size > 100 || name.length() > 50) {
      throw new IdentityException(IdentityException.Reason.INVALID_INPUT, "分页参数不合法");
    }
    return employees.search(name, page, size);
  }

  private void administrator(StaffIdentity identity) {
    var actor = employee(identity.employeeId());
    actor.requireActive(identity.securityVersion());
    actor.requireAdministrator();
  }

  private EmployeeAccount employee(UUID id) {
    return employees
        .findById(id)
        .orElseThrow(() -> new IdentityException(IdentityException.Reason.NOT_FOUND, "员工不存在"));
  }
}
