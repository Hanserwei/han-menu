package com.hanserwei.hanmenu.customer.web.admin;

import com.hanserwei.hanmenu.customer.application.CustomerAdministration;
import com.hanserwei.hanmenu.customer.domain.CustomerSearch;
import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理员顾客资源位于员工安全链，与顾客本人接口隔离. */
@RestController
@RequestMapping("/api/v1/management/customers")
@Tag(name = "管理员顾客管理")
class CustomerManagementController {
  private final CustomerAdministration administration;

  CustomerManagementController(CustomerAdministration administration) {
    this.administration = administration;
  }

  /** 按档案字段和注册时间分页检索，不读取顾客的地址或购物车. */
  @GetMapping
  @Operation(summary = "分页检索顾客档案")
  CustomerAdministration.CustomerPageView search(
      @AuthenticationPrincipal StaffIdentity actor,
      @RequestParam(required = false) String phone,
      @RequestParam(required = false) String name,
      @RequestParam(required = false) Boolean enabled,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return administration.search(
        actor, new CustomerSearch(phone, name, enabled, from, to, page, size));
  }

  /** 读取指定顾客档案. */
  @GetMapping("/{id}")
  @Operation(summary = "读取顾客档案")
  CustomerAdministration.ManagedCustomerView get(
      @AuthenticationPrincipal StaffIdentity actor, @PathVariable UUID id) {
    return administration.get(actor, id);
  }

  /** 返回状态和新资源版本，重复相同状态不会重复撤销会话或登记审计. */
  @PatchMapping("/{id}/status")
  @Operation(summary = "启停用顾客账号", description = "要求当前 version；重新启用不会恢复旧会话")
  CustomerAdministration.ManagedCustomerView changeStatus(
      @AuthenticationPrincipal StaffIdentity actor,
      @PathVariable UUID id,
      @Valid @RequestBody CustomerStatusChange body) {
    return administration.changeStatus(actor, id, body.enabled(), body.version());
  }

  /** 修改状态必须同时给出目标状态和并发版本. */
  record CustomerStatusChange(@NotNull Boolean enabled, @NotNull @Min(0) Long version) {}
}
