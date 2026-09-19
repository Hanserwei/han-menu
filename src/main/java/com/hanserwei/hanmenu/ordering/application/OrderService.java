package com.hanserwei.hanmenu.ordering.application;

import com.google.common.hash.Hashing;
import com.hanserwei.hanmenu.cart.api.CartCheckout;
import com.hanserwei.hanmenu.catalog.api.CatalogCheckout;
import com.hanserwei.hanmenu.customer.api.CustomerAuthorization;
import com.hanserwei.hanmenu.customer.api.CustomerCheckout;
import com.hanserwei.hanmenu.customer.api.CustomerIdentity;
import com.hanserwei.hanmenu.ordering.domain.AddressSnapshot;
import com.hanserwei.hanmenu.ordering.domain.Order;
import com.hanserwei.hanmenu.ordering.domain.OrderException;
import com.hanserwei.hanmenu.ordering.domain.OrderLine;
import com.hanserwei.hanmenu.ordering.domain.OrderRepository;
import com.hanserwei.hanmenu.shop.api.ShopQuery;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 编排未支付订单事务：顾客锁、幂等查重、事实校验、快照落库及购物车结算原子提交. */
@Service
@Transactional
public class OrderService {
  private final OrderRepository orders;
  private final CustomerAuthorization authorization;
  private final CustomerCheckout customers;
  private final ShopQuery shop;
  private final CatalogCheckout catalog;
  private final CartCheckout carts;
  private final Clock clock;

  /** 只通过命名公开契约调用其他模块，事务中不进行外部网络调用. */
  public OrderService(
      OrderRepository orders,
      CustomerAuthorization authorization,
      CustomerCheckout customers,
      ShopQuery shop,
      CatalogCheckout catalog,
      CartCheckout carts,
      Clock clock) {
    this.orders = orders;
    this.authorization = authorization;
    this.customers = customers;
    this.shop = shop;
    this.catalog = catalog;
    this.carts = carts;
    this.clock = clock;
  }

  /**
   * 同键同请求优先返回原订单；所有新的建单检查及结算在同一短事务中完成.
   *
   * @param identity 已认证且仍须在数据库重验的顾客身份
   * @param key 顾客作用域内的提交幂等键，成功后随订单保留
   * @param command 所选地址、购物车版本和结算条目，不包含客户端价格
   * @return 原订单或新订单的当前详情，并标明是否为幂等重放
   * @throws OrderException 幂等键格式错误、复用键改变请求或门店关闭时拒绝建单
   */
  public Submission submit(CustomerIdentity identity, String key, SubmitCommand command) {
    if (key == null || !key.matches("[A-Za-z0-9._:-]{1,128}")) {
      throw new OrderException(
          OrderException.Reason.INVALID_INPUT, "幂等键需为 1 至 128 位字母、数字或 . _ : -");
    }
    customers.lockActive(identity);
    var fingerprint = command.fingerprint();
    var previous = orders.findSubmitted(identity.customerId(), key);
    if (previous.isPresent()) {
      var order = previous.orElseThrow();
      order.requireSameRequest(fingerprint);
      return new Submission(OrderViews.detail(order), true);
    }
    var address = customers.address(identity, command.addressId(), command.addressVersion());
    if (!shop.forCheckout().status().equals("OPEN")) {
      throw new OrderException(OrderException.Reason.SHOP_CLOSED, "门店已打烊");
    }
    var selected = carts.selected(identity, command.cartVersion(), command.itemIds());
    var lines = new ArrayList<OrderLine>();
    for (var item : selected) {
      var quote = catalog.quote(item.productId(), item.selections());
      var product = quote.product();
      lines.add(
          new OrderLine(
              UUID.randomUUID(),
              product.id(),
              product.kind(),
              product.name(),
              product.price(),
              item.quantity(),
              item.selections(),
              quote.components().stream()
                  .map(
                      component ->
                          new OrderLine.Component(
                              component.productId(),
                              component.name(),
                              component.quantity(),
                              component.selections()))
                  .toList()));
    }
    var order =
        Order.submit(
            identity.customerId(),
            key,
            fingerprint,
            new AddressSnapshot(
                address.id(),
                address.version(),
                address.recipientName(),
                address.phone(),
                address.province(),
                address.city(),
                address.district(),
                address.detail()),
            lines,
            clock.instant());
    orders.add(order);
    carts.settle(identity, command.cartVersion(), command.itemIds());
    return new Submission(OrderViews.detail(own(identity, order.id())), false);
  }

  /** 只返回本人订单的不可变成交快照，不受目录或地址删除影响. */
  @Transactional(readOnly = true)
  public OrderViews.Detail detail(CustomerIdentity identity, UUID id) {
    authorization.requireActive(identity);
    return OrderViews.detail(own(identity, id));
  }

  /** 读取本人历史订单；页码零基，单页最多五十条. */
  @Transactional(readOnly = true)
  public OrderViews.History history(CustomerIdentity identity, int page, int size) {
    authorization.requireActive(identity);
    if (page < 0 || page > 10000 || size < 1 || size > 50) {
      throw new OrderException(OrderException.Reason.INVALID_INPUT, "分页参数不合法");
    }
    return OrderViews.history(orders.history(identity.customerId(), page, size), page, size);
  }

  /** 取消仅变更订单状态；不恢复购物车，也不调用尚未实现的支付能力. */
  public OrderViews.Detail cancel(CustomerIdentity identity, UUID id, long version) {
    customers.lockActive(identity);
    var order = own(identity, id);
    order.cancel(version, clock.instant());
    orders.update(order);
    return OrderViews.detail(own(identity, id));
  }

  /**
   * 按当前目录原子合并旧订单全部条目，不复制历史价格或直接创建新订单.
   *
   * @param identity 操作购物车的当前顾客
   * @param id 本人历史订单标识，待付款和已取消订单均可重新加购
   * @param version 客户端读取到的原订单版本
   * @param cartVersion 客户端读取到的购物车版本，重复提交旧版本不能再次加量
   * @return 整体成功后的购物车版本
   * @throws OrderException 订单不属于本人或订单版本已变化时拒绝
   */
  public Reordered reorder(CustomerIdentity identity, UUID id, long version, long cartVersion) {
    customers.lockActive(identity);
    var order = own(identity, id);
    order.requireVersion(version);
    var selections =
        order.lines().stream()
            .map(
                line ->
                    new CartCheckout.Selection(
                        line.id(), line.productId(), line.quantity(), line.selections()))
            .toList();
    return new Reordered(carts.reorder(identity, cartVersion, selections));
  }

  private Order own(CustomerIdentity identity, UUID id) {
    return orders
        .findOwned(identity.customerId(), id)
        .orElseThrow(() -> new OrderException(OrderException.Reason.NOT_FOUND, "订单不存在"));
  }

  /** 提交意图只包含资源标识与版本，条目顺序不影响幂等摘要. */
  public record SubmitCommand(
      UUID addressId, long addressVersion, long cartVersion, List<UUID> itemIds) {
    /** 校验选择集合，并固定请求供事务重试比较. */
    public SubmitCommand {
      if (addressId == null
          || addressVersion < 0
          || cartVersion < 0
          || itemIds == null
          || itemIds.isEmpty()
          || itemIds.size() > 50
          || itemIds.stream().anyMatch(Objects::isNull)
          || new HashSet<>(itemIds).size() != itemIds.size()) {
        throw new OrderException(OrderException.Reason.INVALID_INPUT, "下单标识、版本或条目集合不合法");
      }
      itemIds = itemIds.stream().sorted().toList();
    }

    /** 固定字段边界和排序，避免 JSON 字段或条目顺序导致错误幂等冲突. */
    String fingerprint() {
      return Hashing.sha256()
          .hashString(
              addressId + ":" + addressVersion + ":" + cartVersion + ":" + itemIds,
              StandardCharsets.UTF_8)
          .toString();
    }
  }

  /** 标记是否重放原订单，供 HTTP 区分创建与重试. */
  public record Submission(OrderViews.Detail order, boolean replayed) {}

  /** 返回加购后的购物车版本，客户端随后读取实时购物车. */
  public record Reordered(long cartVersion) {}
}
