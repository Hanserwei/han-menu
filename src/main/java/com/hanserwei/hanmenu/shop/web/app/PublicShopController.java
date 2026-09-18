package com.hanserwei.hanmenu.shop.web.app;

import com.hanserwei.hanmenu.shop.api.ShopQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** App 展示门店状态的只读入口，不需要员工身份或尚未实现的顾客会话. */
@RestController
@SecurityRequirements
@Tag(name = "公开门店")
class PublicShopController {
  private final ShopQuery shop;

  PublicShopController(ShopQuery shop) {
    this.shop = shop;
  }

  @GetMapping("/api/v1/storefront")
  @Operation(summary = "获取门店当前营业状态和联系信息")
  ShopQuery.ShopView current() {
    return shop.current();
  }
}
