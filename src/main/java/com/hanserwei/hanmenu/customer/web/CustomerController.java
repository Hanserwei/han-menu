package com.hanserwei.hanmenu.customer.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hanserwei.hanmenu.customer.api.CustomerIdentity;
import com.hanserwei.hanmenu.customer.application.AddressBookService;
import com.hanserwei.hanmenu.customer.application.CustomerAuthentication;
import com.hanserwei.hanmenu.customer.domain.CustomerAccount;
import com.hanserwei.hanmenu.customer.domain.CustomerPassword;
import com.hanserwei.hanmenu.customer.domain.DeliveryAddress;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Flutter 顾客资源适配器，注册与会话创建分开，地址只允许本人操作. */
@RestController
@RequestMapping("/api/v1/customer")
@Tag(name = "顾客身份与地址")
class CustomerController {
  private final CustomerAuthentication authentication;
  private final AddressBookService addresses;

  CustomerController(CustomerAuthentication authentication, AddressBookService addresses) {
    this.authentication = authentication;
    this.addresses = addresses;
  }

  /** 创建账号后单独登录，避免注册成功但隐式登录失败形成不明确结果. */
  @PostMapping("/accounts")
  @SecurityRequirements
  @Operation(summary = "注册顾客", description = "手机号作为登录标识，当前没有短信验证，不代表已验证手机号归属")
  @ApiResponse(responseCode = "201", description = "顾客账号已创建")
  ResponseEntity<CustomerView> register(
      @Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
    var result =
        authentication.register(
            body.phone(),
            body.displayName(),
            new CustomerPassword(body.password()),
            request.getRemoteAddr());
    return ResponseEntity.status(201).body(CustomerView.from(result));
  }

  /** 创建独立顾客会话，手机号与密码不写入日志. */
  @PostMapping("/sessions")
  @SecurityRequirements
  @Operation(summary = "顾客登录")
  @ApiResponse(responseCode = "201", description = "顾客会话已创建")
  ResponseEntity<SessionView> login(
      @Valid @RequestBody LoginRequest body, HttpServletRequest request) {
    var result = authentication.login(body.phone(), body.password(), request.getRemoteAddr());
    return ResponseEntity.created(URI.create("/api/v1/customer/sessions/current"))
        .body(new SessionView(result.accessToken(), "Bearer", result.expiresAt()));
  }

  /** 撤销单个顾客会话，其他有效会话保持不变. */
  @DeleteMapping("/sessions/current")
  @Operation(summary = "顾客退出")
  @ApiResponse(responseCode = "204", description = "当前顾客会话已撤销")
  ResponseEntity<Void> logout(Authentication context) {
    authentication.logout((String) context.getDetails());
    return ResponseEntity.noContent().build();
  }

  /** 返回当前顾客资料，不返回密码和安全版本. */
  @GetMapping("/me")
  @Operation(summary = "读取本人资料")
  CustomerView me(@AuthenticationPrincipal CustomerIdentity identity) {
    return CustomerView.from(authentication.current(identity));
  }

  /** 修改昵称必须基于当前版本，手机号不会在资料更新中被替换. */
  @PutMapping("/me")
  @Operation(summary = "修改本人昵称")
  CustomerView updateMe(
      @AuthenticationPrincipal CustomerIdentity identity, @Valid @RequestBody ProfileUpdate body) {
    return CustomerView.from(
        authentication.reviseProfile(identity, body.displayName(), body.version()));
  }

  /** 修改密码后撤销所有旧会话，客户端必须重新登录. */
  @PutMapping("/me/password")
  @Operation(summary = "修改本人密码")
  @ApiResponse(responseCode = "204", description = "密码修改成功")
  ResponseEntity<Void> password(
      @AuthenticationPrincipal CustomerIdentity identity, @Valid @RequestBody PasswordChange body) {
    authentication.changePassword(
        identity, body.currentPassword(), new CustomerPassword(body.newPassword()));
    return ResponseEntity.noContent().build();
  }

  /** 查询当前顾客的有界地址簿. */
  @GetMapping("/addresses")
  @Operation(summary = "读取自己的地址簿")
  List<AddressView> list(@AuthenticationPrincipal CustomerIdentity identity) {
    return addresses.list(identity).stream().map(AddressView::from).toList();
  }

  /** 查询单一所属地址，其他顾客的标识不会泄露地址存在性. */
  @GetMapping("/addresses/{id}")
  @Operation(summary = "读取自己的地址")
  AddressView get(@AuthenticationPrincipal CustomerIdentity identity, @PathVariable UUID id) {
    return AddressView.from(addresses.get(identity, id));
  }

  /** 创建地址及可选默认状态. */
  @PostMapping("/addresses")
  @Operation(summary = "创建收货地址")
  @ApiResponse(responseCode = "201", description = "地址已创建")
  ResponseEntity<AddressView> createAddress(
      @AuthenticationPrincipal CustomerIdentity identity, @Valid @RequestBody AddressRequest body) {
    var address = addresses.create(identity, body.toCommand());
    return ResponseEntity.created(URI.create("/api/v1/customer/addresses/" + address.id()))
        .body(AddressView.from(address));
  }

  /** 修改地址及其默认状态，false 会显式取消默认，不会自动指定其他地址. */
  @PutMapping("/addresses/{id}")
  @Operation(summary = "修改收货地址")
  AddressView updateAddress(
      @AuthenticationPrincipal CustomerIdentity identity,
      @PathVariable UUID id,
      @Valid @RequestBody AddressUpdate body) {
    return AddressView.from(
        addresses.update(identity, id, body.address().toCommand(), body.version()));
  }

  /** 原子替换当前顾客的默认地址. */
  @PatchMapping("/addresses/{id}/default")
  @Operation(summary = "设置默认地址")
  AddressView makeDefault(
      @AuthenticationPrincipal CustomerIdentity identity,
      @PathVariable UUID id,
      @Valid @RequestBody Version body) {
    return AddressView.from(addresses.makeDefault(identity, id, body.version()));
  }

  /** 删除当前顾客地址，必须携带读取到的资源版本. */
  @DeleteMapping("/addresses/{id}")
  @Operation(summary = "删除收货地址")
  @ApiResponse(responseCode = "204", description = "地址已删除")
  ResponseEntity<Void> deleteAddress(
      @AuthenticationPrincipal CustomerIdentity identity,
      @PathVariable UUID id,
      @RequestParam @Min(0) long version) {
    addresses.delete(identity, id, version);
    return ResponseEntity.noContent().build();
  }

  /** 注册输入中的密码仅允许写入，默认字符串不暴露任何身份资料. */
  record RegisterRequest(
      @NotBlank @Size(max = 16) String phone,
      @NotBlank @Size(max = 50) String displayName,
      @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank @Size(min = 8, max = 64)
          String password) {
    @Override
    public String toString() {
      return "RegisterRequest[资料与凭证已隐藏]";
    }
  }

  /** 登录输入不允许回显或记录凭证. */
  record LoginRequest(
      @NotBlank @Size(max = 16) String phone,
      @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank @Size(max = 128)
          String password) {
    @Override
    public String toString() {
      return "LoginRequest[凭证已隐藏]";
    }
  }

  /** 顾客令牌仅在本次响应返回. */
  record SessionView(String accessToken, String tokenType, Instant expiresAt) {
    @Override
    public String toString() {
      return "SessionView[令牌已隐藏]";
    }
  }

  /** 本人可读取的顾客资料快照. */
  record CustomerView(UUID id, String phone, String displayName, long version) {
    static CustomerView from(CustomerAccount value) {
      return new CustomerView(value.id(), value.phone(), value.displayName(), value.version());
    }

    @Override
    public String toString() {
      return "CustomerView[id=" + id + "]";
    }
  }

  /** 昵称修改必须明确传递版本. */
  record ProfileUpdate(
      @NotBlank @Size(max = 50) String displayName, @NotNull @Min(0) Long version) {
    @Override
    public String toString() {
      return "ProfileUpdate[资料已隐藏]";
    }
  }

  /** 密码修改仅服务于当前顾客，不能指定他人账号. */
  record PasswordChange(
      @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank @Size(max = 128)
          String currentPassword,
      @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank @Size(min = 8, max = 64)
          String newPassword) {
    @Override
    public String toString() {
      return "PasswordChange[凭证已隐藏]";
    }
  }

  /** 修改资源的必要版本，不允许省略后默认为零. */
  record Version(@NotNull @Min(0) Long version) {}

  /** 地址输入仅允许业务字段，归属标识由当前认证上下文赋值. */
  record AddressRequest(
      @NotBlank @Size(max = 20) String label,
      @NotBlank @Size(max = 50) String recipientName,
      @NotBlank @Size(max = 16) String phone,
      @NotBlank @Size(max = 50) String province,
      @NotBlank @Size(max = 50) String city,
      @NotBlank @Size(max = 50) String district,
      @NotBlank @Size(max = 200) String detail,
      boolean defaultAddress) {
    AddressBookService.AddressCommand toCommand() {
      return new AddressBookService.AddressCommand(
          label, recipientName, phone, province, city, district, detail, defaultAddress);
    }

    @Override
    public String toString() {
      return "AddressRequest[收货资料已隐藏]";
    }
  }

  /** 完整地址替换与版本分离. */
  record AddressUpdate(@NotNull @Valid AddressRequest address, @NotNull @Min(0) Long version) {}

  /** 地址输出不含顾客内部安全状态. */
  record AddressView(
      UUID id,
      String label,
      String recipientName,
      String phone,
      String province,
      String city,
      String district,
      String detail,
      boolean defaultAddress,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    static AddressView from(DeliveryAddress value) {
      return new AddressView(
          value.id(),
          value.label(),
          value.recipientName(),
          value.phone(),
          value.province(),
          value.city(),
          value.district(),
          value.detail(),
          value.defaultAddress(),
          value.version(),
          value.createdAt(),
          value.updatedAt());
    }

    @Override
    public String toString() {
      return "AddressView[id=" + id + "]";
    }
  }
}
