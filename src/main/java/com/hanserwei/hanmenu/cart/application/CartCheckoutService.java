package com.hanserwei.hanmenu.cart.application;

import com.hanserwei.hanmenu.cart.api.CartCheckout;
import com.hanserwei.hanmenu.cart.domain.CartException;
import com.hanserwei.hanmenu.cart.domain.CartItem;
import com.hanserwei.hanmenu.cart.domain.CartRepository;
import com.hanserwei.hanmenu.cart.domain.ShoppingCart;
import com.hanserwei.hanmenu.catalog.api.CatalogCheckout;
import com.hanserwei.hanmenu.customer.api.CustomerAuthorization;
import com.hanserwei.hanmenu.customer.api.CustomerIdentity;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 在订单事务中执行购物车结算，ORM 版本锁阻止并发加购被误删. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
class CartCheckoutService implements CartCheckout {
  private final CartRepository carts;
  private final CustomerAuthorization authorization;
  private final CatalogCheckout catalog;
  private final Clock clock;

  CartCheckoutService(
      CartRepository carts,
      CustomerAuthorization authorization,
      CatalogCheckout catalog,
      Clock clock) {
    this.carts = carts;
    this.authorization = authorization;
    this.catalog = catalog;
    this.clock = clock;
  }

  @Override
  public List<Selection> selected(CustomerIdentity identity, long version, List<UUID> itemIds) {
    authorization.requireActive(identity);
    var cart = cart(identity);
    cart.requireVersion(version);
    return cart.selected(itemIds).stream()
        .map(item -> new Selection(item.id(), item.productId(), item.quantity(), item.selections()))
        .toList();
  }

  @Override
  public long settle(CustomerIdentity identity, long version, List<UUID> itemIds) {
    authorization.requireActive(identity);
    var cart = cart(identity);
    cart.requireVersion(version);
    carts.update(cart.settle(itemIds, clock.instant()));
    return cart(identity).version();
  }

  @Override
  public long reorder(CustomerIdentity identity, long version, List<Selection> items) {
    authorization.requireActive(identity);
    if (items.isEmpty() || items.size() > 50) {
      throw new CartException(CartException.Reason.INVALID_INPUT, "重新加购条目数量不合法");
    }
    var current = carts.findByCustomer(identity.customerId());
    var cart = current.orElseGet(() -> ShoppingCart.empty(identity.customerId(), clock.instant()));
    cart.requireVersion(version);
    for (var item : items) {
      var product = catalog.quote(item.productId(), item.selections()).product();
      cart =
          cart.add(
              new CartItem(
                  UUID.randomUUID(),
                  product.id(),
                  product.kind(),
                  product.name(),
                  product.price(),
                  item.quantity(),
                  item.selections()),
              item.quantity(),
              clock.instant());
    }
    if (current.isPresent()) {
      carts.update(cart);
    } else {
      carts.add(cart);
    }
    return cart(identity).version();
  }

  private ShoppingCart cart(CustomerIdentity identity) {
    return carts
        .findByCustomer(identity.customerId())
        .orElseThrow(() -> new CartException(CartException.Reason.NOT_FOUND, "购物车不存在"));
  }
}
