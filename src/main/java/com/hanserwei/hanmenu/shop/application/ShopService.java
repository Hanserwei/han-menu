package com.hanserwei.hanmenu.shop.application;

import com.hanserwei.hanmenu.identity.api.StaffAuthorization;
import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import com.hanserwei.hanmenu.shop.api.ShopQuery;
import com.hanserwei.hanmenu.shop.domain.Shop;
import com.hanserwei.hanmenu.shop.domain.ShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 门店应用用例，通过身份模块公开契约授权，并在事务内调用聚合行为. */
@Service
@Transactional
public class ShopService implements ShopQuery {
  private final ShopRepository repository;
  private final StaffAuthorization authorization;

  /** 注入端口，实现与身份内部模型解耦. */
  public ShopService(ShopRepository repository, StaffAuthorization authorization) {
    this.repository = repository;
    this.authorization = authorization;
  }

  /** 读取持久化营业状态. */
  @Override
  @Transactional(readOnly = true)
  public ShopView current() {
    return view(repository.get());
  }

  /** 按客户端版本修改资料并返回新版本. */
  public ShopView revise(
      StaffIdentity actor, String name, String phone, String address, long version) {
    authorization.requireAdministrator(actor);
    var shop = repository.get();
    shop.requireVersion(version);
    shop.revise(name, phone, address);
    repository.save(shop);
    return view(repository.get());
  }

  /** 开店或打烊必须通过聚合规则和 ORM 并发保护. */
  public ShopView changeStatus(StaffIdentity actor, boolean open, long version) {
    authorization.requireAdministrator(actor);
    var shop = repository.get();
    shop.requireVersion(version);
    shop.changeOpen(open);
    repository.save(shop);
    return view(repository.get());
  }

  private ShopView view(Shop shop) {
    return new ShopView(
        shop.name(), shop.phone(), shop.address(), shop.open() ? "OPEN" : "CLOSED", shop.version());
  }
}
