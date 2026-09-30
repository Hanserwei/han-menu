# catalog —— 商品目录（分类 / 菜品 / 套餐 / 图片）

代码位置：`src/main/java/com/hanserwei/hanmenu/catalog/`

## 职责与边界

- 维护分类、可售商品（菜品 DISH / 套餐 SET_MEAL）、口味规则、套餐组成与商品图片。
- 目录写操作在**目录修订行锁**下串行完成分类/套餐引用检查；图片存储调用不占数据库长事务。
- 公开目录缓存可回源数据库，使用同事务目录修订号隔离代际；
  **缓存只做展示加速，不得以缓存控制商品或门店业务状态**。

## 聚合根

### Category

| 业务方法 | 说明 |
| --- | --- |
| `revise(name, sortOrder)` | 修改名称与排序。 |
| `changeEnabled(bool)` | 启停用分类。 |
| `requireVersion(expected)` | 乐观锁校验。 |

`kind`（DISH / SET_MEAL）创建后不可改变；跨聚合引用检查由目录应用服务在修订锁下完成。

### MenuProduct

可售商品聚合，共享定价和上下架行为，并按 `ProductKind` 保护菜品/套餐各自的不变量。

| 业务方法 | 说明 |
| --- | --- |
| `revise(categoryId, name, description, price, imageId, flavors, components)` | 修改商品资料；按种类校验口味/组成约束。 |
| `changeSale(bool)` | 上架/下架。 |
| `validateSelections(Map<String,String>)` | 校验顾客口味选择满足口味组规则（必选、单选、值合法）。 |
| `requireVersion(expected)` | 乐观锁校验。 |

不变量：

- 套餐的 `components` 只能引用 DISH，**套餐不能嵌套套餐**。
- 口味组为单选；`FlavorGroup` 名称和可选值在聚合内唯一。
- `ProductKind` 创建后不可变，防止绕过套餐与菜品约束。

## 值对象

| 值对象 | 约束 |
| --- | --- |
| `Money(amount)` | 人民币金额，精确到分；**不允许零、负数或隐式舍入**。 |
| `FlavorGroup(name, options, required)` | 单选口味组；名称与可选值聚合内唯一。 |
| `MealComponent(dishId, quantity, selections)` | 套餐内一款菜品的数量与确定的口味选择。 |
| `CatalogImage(id, objectKey, mediaType, size, createdAt)` | 图片元数据只保存受控对象键和媒体类型，**不持久化临时签名 URL**。 |
| `ProductPage` | 仓储查询快照，不暴露 ORM 分页类型。 |

## 端口

| 端口 | 职责 |
| --- | --- |
| `CatalogRepository` | 目录仓储；跨聚合变更在目录修订锁下串行。 |
| `ImageStorage` | 私有对象存储端口；上传路径由服务端决定，业务不接触渠道密钥。 |
| `CatalogCache` | 公开目录序列化快照缓存；失败回源数据库，不作为业务真相来源。 |

## 领域异常

`CatalogException.Reason`：`INVALID_INPUT`、`NOT_FOUND`、`CONFLICT`、`VERSION_CONFLICT`、
`UNAVAILABLE`（外部服务不可用）

## 公开契约与集成事件

`api` 包：

- `CatalogQuery` —— 购物车及订单阶段的商品查询；**下单校验应读取实时事实而非缓存展示数据**。
- `CatalogCheckout` —— 结算时直接读取并锁定目录，校验菜品和套餐组成，提供服务端价格；
  `QuotedProduct` 携带 `ComponentSnapshot`（当时名称与固定规格），**后续目录修改不影响订单历史**。
- `CatalogSummary` —— 员工工作台目录规模摘要（`Counts`：在售/停售的菜品与套餐数）。
- `CatalogViews` —— 公开不可变查询模型（`CategoryView`/`ProductView` 等），
  不含领域聚合、ORM 实体或临时签名 URL。

`events` 包：当前无集成事件。

## 关键业务规则

1. 定价服务端唯一：购物车展示价仅供参考，订单价格必须由 `CatalogCheckout` 重新计价。
2. 目录写操作（改分类、改商品、上下架）持修订行锁串行，保证套餐引用的菜品/分类一致。
3. 图片上传/删除调用对象存储不占数据库长事务；失败需补偿（孤儿对象清理）。
4. 口味选择校验（`validateSelections`）是订单结算链路的一部分，规则在聚合内闭环。
5. 缓存按目录修订号隔离代际，回源数据库时以同事务修订号判断可见性。

## 相关文档

- [P3_CONTRACT.md](../P3_CONTRACT.md)（目录与门店接口契约）
- [P5_CONTRACT.md](../P5_CONTRACT.md)（结算计价协作）
- 数据库：`V3__catalog_and_shop.sql`
