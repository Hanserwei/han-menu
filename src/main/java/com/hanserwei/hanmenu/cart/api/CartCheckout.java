package com.hanserwei.hanmenu.cart.api;

import com.hanserwei.hanmenu.customer.api.CustomerIdentity;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 订单对购物车的结算与重新加购契约，所有操作加入调用方事务. */
public interface CartCheckout {
  /** 校验购物车版本及所选条目归属，返回结算数量和规格；不提供历史展示价格. */
  List<Selection> selected(CustomerIdentity identity, long version, List<UUID> itemIds);

  /**
   * 只移除指定版本中已结算的条目，并返回新版本；并发冲突使整个订单事务回滚.
   *
   * @param identity 拥有购物车的当前顾客
   * @param version 生成本次订单选择时的购物车版本
   * @param itemIds 已保存为订单快照的完整条目标识，不支持部分数量结算
   * @return 移除条目后持久化的购物车版本
   */
  long settle(CustomerIdentity identity, long version, List<UUID> itemIds);

  /** 按当前可售状态及价格原子合并全部订单条目；任一条目失效或超限时全部回滚. */
  long reorder(CustomerIdentity identity, long version, List<Selection> items);

  /** 购物车选择不携带可信价格，订单必须调用目录重新计价. */
  record Selection(UUID itemId, UUID productId, int quantity, Map<String, String> selections) {
    /** 固定规格映射. */
    public Selection {
      selections = Map.copyOf(selections);
    }
  }
}
