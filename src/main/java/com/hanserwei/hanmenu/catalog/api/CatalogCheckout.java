package com.hanserwei.hanmenu.catalog.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 结算时直接读取并锁定目录，校验菜品和套餐组成，提供服务端价格. */
public interface CatalogCheckout {
  /**
   * 在当前事务固定目录代际并验证商品与规格，失效时整体拒绝.
   *
   * @param productId 待结算或重新加购的商品标识
   * @param selections 顾客选择的规格；套餐必须为空，其组成使用目录内固定规格
   * @return 当前服务端价格及组成快照，目录锁保持到调用方事务结束
   */
  QuotedProduct quote(UUID productId, Map<String, String> selections);

  /** 套餐组成包含当时名称和固定规格，后续目录修改不影响订单历史. */
  record ComponentSnapshot(
      UUID productId, String name, int quantity, Map<String, String> selections) {
    /** 固定规格快照. */
    public ComponentSnapshot {
      selections = Map.copyOf(selections);
    }
  }

  /** 商品当前价格及套餐组成快照. */
  record QuotedProduct(CatalogViews.ProductView product, List<ComponentSnapshot> components) {
    /** 固定套餐组成快照. */
    public QuotedProduct {
      components = List.copyOf(components);
    }
  }
}
