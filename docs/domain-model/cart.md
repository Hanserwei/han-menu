# cart —— 顾客购物车

代码位置：`src/main/java/com/hanserwei/hanmenu/cart/`

## 职责与边界

- 保存顾客结算前的商品选择与展示快照，仅通过 customer/catalog 的公开 API 访问业务能力。
- 购物车**不产生订单价格**：条目中的单价仅为展示快照，订单结算必须由目录重新计价。

## 聚合根：ShoppingCart

顾客购物车聚合，封装同商品同规格合并和条目数量变更。

| 业务方法 | 说明 |
| --- | --- |
| `empty(customerId, now)` | 创建空车（每顾客至多一辆）。 |
| `add(item, amount, now)` | 加购；**同商品同规格自动合并数量**。 |
| `changeQuantity(itemId, quantity, now)` | 修改条目数量。 |
| `remove(itemId, now)` | 移除条目。 |
| `selected(itemIds)` | 提取选中条目（结算输入）。 |
| `settle(itemIds, now)` | 结算：移除已成交条目，保留未选中条目。 |
| `clear(now)` | 清空购物车。 |
| `requireVersion(expected)` | 乐观锁校验。 |

## 值对象

`CartItem(id, productId, productKind, productName, unitPrice, quantity, selections)`：

- 保存展示快照，但**不作为订单最终价格来源**。
- `sameSelection(productId, selections)` 判定同商品同规格，用于合并。
- `addQuantity(amount)` / `changeQuantity(replacement)` 返回新条目（不可变风格）。

## 端口

| 端口 | 职责 |
| --- | --- |
| `CartRepository` | 购物车聚合仓储。 |

## 领域异常

`CartException.Reason`：`INVALID_INPUT`、`NOT_FOUND`、`CONFLICT`、`VERSION_CONFLICT`、
`UNAVAILABLE`（外部依赖不可用）；含商品不可用、版本冲突和顾客输入错误。

## 公开契约与集成事件

`api` 包：

- `CartCheckout` —— 订单对购物车的结算与重新加购契约，**所有操作加入调用方事务**
  （订单结算在同一事务内扣减购物车）。
- `Selection(itemId, productId, quantity, selections)` —— 购物车选择不携带可信价格，
  订单必须调用目录重新计价。

`events` 包：当前无集成事件。

## 关键业务规则

1. 同一商品、同一口味规格的条目在加购时合并数量。
2. 结算只移除选中条目；下单失败（如价格变动）可通过 `CartCheckout` 重新加购。
3. 购物车条目价格仅做展示；订单金额一律以 `CatalogCheckout` 服务端计价为准。
4. 购物车操作通过顾客身份校验（`CustomerAuthorization`），不支持匿名购物车。

## 相关文档

- [P4_CONTRACT.md](../P4_CONTRACT.md)（购物车接口契约）
- [catalog.md](catalog.md)（计价规则）、[customer.md](customer.md)（顾客身份）
- 数据库：`V4__customer_and_cart.sql`
