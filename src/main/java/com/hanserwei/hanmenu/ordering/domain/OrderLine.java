package com.hanserwei.hanmenu.ordering.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 订单条目保留成交时商品、规格、套餐组成及人民币价格，后续不可重新计价. */
public record OrderLine(
    UUID id,
    UUID productId,
    String kind,
    String name,
    BigDecimal unitPrice,
    int quantity,
    Map<String, String> selections,
    List<Component> components) {
  /** 验证正金额和数量，并固定全部集合. */
  public OrderLine {
    Objects.requireNonNull(id);
    Objects.requireNonNull(productId);
    if ((!"DISH".equals(kind) && !"SET_MEAL".equals(kind))
        || name == null
        || name.isBlank()
        || unitPrice == null
        || unitPrice.signum() <= 0
        || quantity < 1
        || quantity > 99) {
      throw new OrderException(OrderException.Reason.INVALID_INPUT, "订单条目不合法");
    }
    unitPrice = unitPrice.setScale(2, RoundingMode.UNNECESSARY);
    selections = Map.copyOf(selections);
    components = List.copyOf(components);
    if ((kind.equals("DISH") && !components.isEmpty())
        || (kind.equals("SET_MEAL") && (components.isEmpty() || !selections.isEmpty()))) {
      throw new OrderException(OrderException.Reason.INVALID_INPUT, "套餐快照不合法");
    }
  }

  /** 使用精确十进制金额计算小计. */
  public BigDecimal subtotal() {
    return unitPrice.multiply(BigDecimal.valueOf(quantity));
  }

  /** 套餐固定组成不随原菜品名称或口味修改. */
  public record Component(
      UUID productId, String name, int quantity, Map<String, String> selections) {
    /** 校验组成并固定规格. */
    public Component {
      Objects.requireNonNull(productId);
      if (name == null || name.isBlank() || quantity < 1 || quantity > 99) {
        throw new IllegalArgumentException("套餐组成快照不合法");
      }
      selections = Map.copyOf(selections);
    }
  }
}
