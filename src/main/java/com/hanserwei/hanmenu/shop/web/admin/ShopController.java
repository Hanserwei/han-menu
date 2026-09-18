package com.hanserwei.hanmenu.shop.web.admin;

import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import com.hanserwei.hanmenu.shop.api.ShopQuery;
import com.hanserwei.hanmenu.shop.application.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端的单店配置入口，变更必须有管理员身份与资源版本. */
@RestController
@RequestMapping("/api/v1/shop")
@Tag(name = "门店管理")
class ShopController {
  private final ShopService shop;

  ShopController(ShopService shop) {
    this.shop = shop;
  }

  @GetMapping
  @Operation(summary = "读取门店配置")
  ShopQuery.ShopView get() {
    return shop.current();
  }

  @PutMapping
  @Operation(summary = "修改门店名称、电话和地址")
  ShopQuery.ShopView revise(
      @AuthenticationPrincipal StaffIdentity actor, @Valid @RequestBody Profile body) {
    return shop.revise(actor, body.name(), body.phone(), body.address(), body.version());
  }

  @PatchMapping("/status")
  @Operation(summary = "开店或打烊")
  ShopQuery.ShopView status(
      @AuthenticationPrincipal StaffIdentity actor, @Valid @RequestBody StatusChange body) {
    return shop.changeStatus(actor, body.status() == OpeningStatus.OPEN, body.version());
  }

  /** 名称必填；未营业时允许先保存不完整联系方式. */
  record Profile(
      @NotBlank @Size(max = 100) String name,
      @NotNull @Size(max = 16) String phone,
      @NotNull @Size(max = 300) String address,
      @NotNull @Min(0) Long version) {}

  /** 营业状态必须显式修改，不能由缓存覆盖. */
  record StatusChange(@NotNull OpeningStatus status, @NotNull @Min(0) Long version) {}

  /** 门店营业状态. */
  enum OpeningStatus {
    OPEN,
    CLOSED
  }
}
