package com.hanserwei.hanmenu.customer.application;

import com.hanserwei.hanmenu.customer.domain.CustomerAccount;
import com.hanserwei.hanmenu.customer.domain.CustomerException;
import com.hanserwei.hanmenu.customer.domain.CustomerRepository;
import com.hanserwei.hanmenu.customer.domain.CustomerSearch;
import com.hanserwei.hanmenu.identity.api.StaffAudit;
import com.hanserwei.hanmenu.identity.api.StaffAuthorization;
import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理员顾客用例只开放档案查询与启停用，不代替顾客修改地址或密码. */
@Service
@Transactional(readOnly = true)
public class CustomerAdministration {
  private final CustomerRepository customers;
  private final StaffAuthorization staff;
  private final StaffAudit audit;
  private final Clock clock;

  /** 注入本模块仓储及公开员工授权和审计契约. */
  public CustomerAdministration(
      CustomerRepository customers, StaffAuthorization staff, StaffAudit audit, Clock clock) {
    this.customers = customers;
    this.staff = staff;
    this.audit = audit;
    this.clock = clock;
  }

  /** 只允许有效管理员执行真实数据库分页. */
  public CustomerPageView search(StaffIdentity actor, CustomerSearch search) {
    staff.requireAdministrator(actor);
    var result = customers.search(search);
    return new CustomerPageView(
        result.items().stream().map(ManagedCustomerView::from).toList(),
        search.page(),
        search.size(),
        result.totalElements(),
        Math.ceilDiv(result.totalElements(), search.size()));
  }

  /** 管理员读取单个顾客档案，响应不包含凭证或地址簿. */
  public ManagedCustomerView get(StaffIdentity actor, UUID id) {
    staff.requireAdministrator(actor);
    return ManagedCustomerView.from(
        customers
            .findById(id)
            .orElseThrow(() -> new CustomerException(CustomerException.Reason.NOT_FOUND, "顾客不存在")));
  }

  /** 在账号锁内验证版本并变更状态；旧会话不随重新启用而恢复，审计与变更同事务. */
  @Transactional
  public ManagedCustomerView changeStatus(
      StaffIdentity actor, UUID id, boolean enabled, long version) {
    staff.requireAdministrator(actor);
    var customer = customers.lock(id);
    customer.requireVersion(version);
    if (customer.enabled() != enabled) {
      customer.changeEnabled(enabled, clock.instant());
      customers.update(customer);
      audit.customerStatusChanged(actor, id);
    }
    return ManagedCustomerView.from(customers.findById(id).orElseThrow());
  }

  /** 管理端最小档案 DTO，不暴露密码摘要及安全版本. */
  public record ManagedCustomerView(
      UUID id,
      String phone,
      String displayName,
      boolean enabled,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    static ManagedCustomerView from(CustomerAccount value) {
      return new ManagedCustomerView(
          value.id(),
          value.phone(),
          value.displayName(),
          value.enabled(),
          value.version(),
          value.createdAt(),
          value.updatedAt());
    }

    /** 调试信息不输出顾客个人资料. */
    @Override
    public String toString() {
      return "ManagedCustomerView[id=" + id + "]";
    }
  }

  /** 顾客零基分页响应. */
  public record CustomerPageView(
      List<ManagedCustomerView> items, int page, int size, long totalElements, long totalPages) {
    /** 固定响应集合. */
    public CustomerPageView {
      items = List.copyOf(items);
    }
  }
}
