package com.hanserwei.hanmenu.cart.application;

import com.hanserwei.hanmenu.cart.domain.CartException;
import com.hanserwei.hanmenu.cart.domain.CartItem;
import com.hanserwei.hanmenu.cart.domain.CartRepository;
import com.hanserwei.hanmenu.cart.domain.ShoppingCart;
import com.hanserwei.hanmenu.catalog.api.CatalogQuery;
import com.hanserwei.hanmenu.catalog.api.CatalogViews;
import com.hanserwei.hanmenu.customer.api.CustomerAuthorization;
import com.hanserwei.hanmenu.customer.api.CustomerIdentity;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 购物车用例只调用公开模块契约，所有写操作校验顾客身份与购物车版本. */
@Service
@Transactional
public class CartService {
  private final CartRepository carts;
  private final CatalogQuery catalog;
  private final CustomerAuthorization authorization;
  private final Clock clock;

  /** 组合顾客授权、实时目录与购物车仓储，避免跨模块读取业务表. */
  public CartService(
      CartRepository carts,
      CatalogQuery catalog,
      CustomerAuthorization authorization,
      Clock clock) {
    this.carts = carts;
    this.catalog = catalog;
    this.authorization = authorization;
    this.clock = clock;
  }

  /** 空购物车读取不创建数据库资源；版本为零，下次首次写入也要求版本零. */
  @Transactional(readOnly = true)
  public CartView get(CustomerIdentity identity) {
    authorization.requireActive(identity);
    return view(
        carts
            .findByCustomer(identity.customerId())
            .orElseGet(() -> ShoppingCart.empty(identity.customerId(), clock.instant())));
  }

  /** 仅接收商品标识、数量及规格，价格和名称均由服务端取得. */
  public CartView add(
      CustomerIdentity identity,
      UUID productId,
      int quantity,
      Map<String, String> selections,
      long version) {
    authorization.requireActive(identity);
    var product = catalog.availableProduct(productId);
    requireSelections(product, selections);
    var current = carts.findByCustomer(identity.customerId());
    var cart = current.orElseGet(() -> ShoppingCart.empty(identity.customerId(), clock.instant()));
    cart.requireVersion(version);
    var item =
        new CartItem(
            UUID.randomUUID(),
            product.id(),
            product.kind(),
            product.name(),
            product.price(),
            quantity,
            selections);
    var next = cart.add(item, quantity, clock.instant());
    if (current.isPresent()) {
      carts.update(next);
    } else {
      carts.add(next);
    }
    return persistedView(identity);
  }

  /** 增加或修改数量时仍须确认该商品及规格有效，过时条目可删除但不能继续加量. */
  public CartView changeQuantity(
      CustomerIdentity identity, UUID itemId, int quantity, long version) {
    authorization.requireActive(identity);
    var cart = cart(identity);
    cart.requireVersion(version);
    var item =
        cart.items().stream()
            .filter(value -> value.id().equals(itemId))
            .findFirst()
            .orElseThrow(() -> new CartException(CartException.Reason.NOT_FOUND, "购物车条目不存在"));
    requireSelections(catalog.availableProduct(item.productId()), item.selections());
    carts.update(cart.changeQuantity(itemId, quantity, clock.instant()));
    return persistedView(identity);
  }

  /** 删除当前顾客的条目，外部顾客的条目标识不能影响此购物车. */
  public CartView remove(CustomerIdentity identity, UUID itemId, long version) {
    authorization.requireActive(identity);
    var cart = cart(identity);
    cart.requireVersion(version);
    carts.update(cart.remove(itemId, clock.instant()));
    return persistedView(identity);
  }

  /** 清空购物车也保留聚合版本，避免陈旧客户端删除后续新加条目. */
  public CartView clear(CustomerIdentity identity, long version) {
    authorization.requireActive(identity);
    var current = carts.findByCustomer(identity.customerId());
    if (current.isEmpty()) {
      var empty = ShoppingCart.empty(identity.customerId(), clock.instant());
      empty.requireVersion(version);
      return view(empty);
    }
    var cart = current.orElseThrow();
    cart.requireVersion(version);
    carts.update(cart.clear(clock.instant()));
    return persistedView(identity);
  }

  private ShoppingCart cart(CustomerIdentity identity) {
    return carts
        .findByCustomer(identity.customerId())
        .orElseThrow(() -> new CartException(CartException.Reason.NOT_FOUND, "购物车不存在"));
  }

  private CartView persistedView(CustomerIdentity identity) {
    return view(cart(identity));
  }

  private CartView view(ShoppingCart cart) {
    // 同一商品不同规格只读取一次目录，最大五十条的购物车保持有界。
    var products = new java.util.HashMap<UUID, Optional<CatalogViews.ProductView>>();
    var items =
        cart.items().stream()
            .map(
                item -> {
                  var product =
                      products.computeIfAbsent(item.productId(), catalog::findAvailableProduct);
                  boolean available =
                      product.isPresent()
                          && validSelections(product.orElseThrow(), item.selections());
                  BigDecimal price =
                      product.map(CatalogViews.ProductView::price).orElse(item.unitPrice());
                  String name =
                      product.map(CatalogViews.ProductView::name).orElse(item.productName());
                  return new ItemView(
                      item.id(),
                      item.productId(),
                      item.productKind(),
                      name,
                      price,
                      item.quantity(),
                      item.selections(),
                      available,
                      available
                          ? null
                          : product.isEmpty() ? "PRODUCT_UNAVAILABLE" : "SELECTION_UNAVAILABLE",
                      price.multiply(BigDecimal.valueOf(item.quantity())));
                })
            .toList();
    BigDecimal total =
        items.stream()
            .filter(ItemView::available)
            .map(ItemView::subtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new CartView(items, cart.version(), cart.updatedAt(), total, "CNY");
  }

  private void requireSelections(CatalogViews.ProductView product, Map<String, String> selections) {
    if (!validSelections(product, selections)) {
      throw new CartException(CartException.Reason.INVALID_INPUT, "规格选择不完整或不合法");
    }
  }

  private boolean validSelections(
      CatalogViews.ProductView product, Map<String, String> selections) {
    if (selections == null || selections.size() > 10) {
      return false;
    }
    if (!product.kind().equals("DISH")) {
      return selections.isEmpty();
    }
    if (selections.keySet().stream()
        .anyMatch(key -> product.flavors().stream().noneMatch(group -> group.name().equals(key)))) {
      return false;
    }
    for (var group : product.flavors()) {
      String selected = selections.get(group.name());
      if (group.required() && selected == null
          || selected != null && !group.options().contains(selected)) {
        return false;
      }
    }
    return true;
  }

  /** 展示快照根据当前目录刷新，金额不作为未来订单的支付依据. */
  public record ItemView(
      UUID id,
      UUID productId,
      String kind,
      String name,
      BigDecimal unitPrice,
      int quantity,
      Map<String, String> selections,
      boolean available,
      String unavailableReason,
      BigDecimal subtotal) {
    /** 固定规格集合. */
    public ItemView {
      selections = Map.copyOf(selections);
    }
  }

  /** 估算总价只包含当前可售且规格合法的条目，下单时还须重新校验. */
  public record CartView(
      List<ItemView> items,
      long version,
      Instant updatedAt,
      BigDecimal estimatedTotal,
      String currency) {
    /** 固定购物车快照，避免调用方篡改集合. */
    public CartView {
      items = List.copyOf(items);
    }
  }
}
