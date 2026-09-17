package com.hanserwei.hanmenu.identity.application;

import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import com.hanserwei.hanmenu.identity.domain.AuditTrail;
import com.hanserwei.hanmenu.identity.domain.EmployeeAccount;
import com.hanserwei.hanmenu.identity.domain.EmployeeProfile;
import com.hanserwei.hanmenu.identity.domain.EmployeeRepository;
import com.hanserwei.hanmenu.identity.domain.IdentityException;
import com.hanserwei.hanmenu.identity.domain.NewPassword;
import com.hanserwei.hanmenu.identity.domain.PasswordHasher;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 员工管理用例，编排授权、聚合行为和持久化，不直接访问数据库或 HTTP. */
@Service
@Transactional
public class EmployeeAdministration {
  private final EmployeeRepository employees;
  private final PasswordHasher passwords;
  private final AuditTrail audit;
  private final TokenFactory tokens;
  private final Clock clock;

  /** 通过构造器组合业务端口，隔离密码算法和持久化实现. */
  public EmployeeAdministration(
      EmployeeRepository employees,
      PasswordHasher passwords,
      AuditTrail audit,
      TokenFactory tokens,
      Clock clock) {
    this.employees = employees;
    this.passwords = passwords;
    this.audit = audit;
    this.tokens = tokens;
    this.clock = clock;
  }

  /** 首次部署时创建管理员；已经存在时不重置密码或提升其他账号角色. */
  public void bootstrap(String username, NewPassword password) {
    if (employees.hasAdministrator()) {
      return;
    }
    String normalized = EmployeeProfile.normalizeUsername(username);
    var existing = employees.findByUsername(normalized);
    if (existing.isPresent()) {
      existing.orElseThrow().requireAdministrator();
      return;
    }
    var account =
        EmployeeAccount.create(
            employees.nextIdentity(),
            new EmployeeProfile(normalized, "系统管理员", "", "2", ""),
            passwords.encode(password),
            EmployeeAccount.Role.ADMIN,
            clock.instant());
    employees.add(account);
    audit.record(AuditTrail.Action.BOOTSTRAP, account.id(), account.id(), true);
  }

  /** 创建普通员工；接口请求无法指定管理员角色，生成密码仅在当前响应返回. */
  public CreatedEmployee create(StaffIdentity actor, EmployeeProfile profile, String password) {
    administrator(actor);
    String initial = password == null ? tokens.newPassword() : password;
    var account =
        EmployeeAccount.create(
            employees.nextIdentity(),
            profile,
            passwords.encode(new NewPassword(initial)),
            EmployeeAccount.Role.STAFF,
            clock.instant());
    employees.add(account);
    audit.record(AuditTrail.Action.CREATE_EMPLOYEE, actor.employeeId(), account.id(), true);
    return new CreatedEmployee(account.id(), password == null ? initial : null);
  }

  /** 更新员工资料，保留角色和密码；可选版本用于检测页面编辑期间的并发修改. */
  public void revise(StaffIdentity actor, long id, EmployeeProfile profile, Long version) {
    administrator(actor);
    var account = employee(id);
    account.requireVersion(version);
    account.reviseProfile(profile, clock.instant());
    employees.update(account);
    audit.record(AuditTrail.Action.UPDATE_EMPLOYEE, actor.employeeId(), id, true);
  }

  /** 启停用账号，由聚合决定管理员保护与会话撤销规则. */
  public void changeStatus(StaffIdentity actor, long id, boolean enabled, Long version) {
    administrator(actor);
    var account = employee(id);
    account.requireVersion(version);
    account.changeEnabled(enabled, clock.instant());
    employees.update(account);
    audit.record(AuditTrail.Action.CHANGE_STATUS, actor.employeeId(), id, true);
  }

  /** 修改当前员工自己的密码，必须提供旧密码；提交后全部旧会话失效. */
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

  /** 管理员读取指定员工，控制器仅映射允许暴露的字段. */
  @Transactional(readOnly = true)
  public EmployeeAccount get(StaffIdentity actor, long id) {
    administrator(actor);
    return employee(id);
  }

  /** 返回受限分页结果，避免任意页大小造成无界数据读取. */
  @Transactional(readOnly = true)
  public EmployeePage page(StaffIdentity actor, String name, int page, int size) {
    administrator(actor);
    if (page < 1 || page > 10000 || size < 1 || size > 100 || name.length() > 50) {
      throw new IdentityException(IdentityException.Reason.INVALID_INPUT, "分页参数不合法");
    }
    return new EmployeePage(employees.count(name), employees.page(name, (page - 1) * size, size));
  }

  private void administrator(StaffIdentity identity) {
    var actor = employee(identity.employeeId());
    actor.requireActive(identity.securityVersion());
    actor.requireAdministrator();
  }

  private EmployeeAccount employee(long id) {
    return employees
        .findById(id)
        .orElseThrow(() -> new IdentityException(IdentityException.Reason.NOT_FOUND, "员工不存在"));
  }

  /** 新账号标识及可选的一次性初始密码，不允许默认字符串输出密码. */
  public record CreatedEmployee(long id, String initialPassword) {
    @Override
    public String toString() {
      return "CreatedEmployee[id=" + id + ", password=已隐藏]";
    }
  }

  /** 应用层分页结果，领域对象由接口层映射后才能响应客户端. */
  public record EmployeePage(long total, List<EmployeeAccount> records) {
    /** 复制集合以避免调用方改变分页快照. */
    public EmployeePage {
      records = List.copyOf(records);
    }
  }
}
