# shop —— 门店经营状态

代码位置：`src/main/java/com/hanserwei/hanmenu/shop/`

## 职责与边界

- 单店（single-store）资料与营业开关，是全系统唯一的门店事实来源。
- 下单前由 ordering 通过 `ShopQuery` 获取实时营业快照并再次验证；
  营业状态是数据库事实，不做缓存。

## 聚合根：Shop

| 业务方法 | 说明 |
| --- | --- |
| `revise(name, phone, address)` | 修改门店资料。 |
| `changeOpen(bool)` | 开店/关店；**资料不完整时不能开店**。 |
| `requireVersion(expected)` | 乐观锁校验（ORM 版本锁阻止并发状态覆盖）。 |

状态：`name`、`phone`、`address`、`open`、`version`。

## 值对象

无独立值对象；对外查询模型 `ShopView(name, phone, address, status, version)`
不含领域对象或 ORM 类型。

## 端口

| 端口 | 职责 |
| --- | --- |
| `ShopRepository` | 单店持久化端口，版本锁阻止并发覆盖。 |

## 领域异常

`ShopException.Reason`：`INVALID_INPUT`、`CONFLICT`、`VERSION_CONFLICT`
（不依赖 Web 状态码，映射由 web 层完成）。

## 公开契约与集成事件

`api` 包：

- `ShopQuery` —— 对外提供营业规则的实时快照，`ShopView.status` 为具名状态值。

`events` 包：当前无集成事件。

## 关键业务规则

1. 资料不完整（缺名称/电话/地址）时不能置为营业中。
2. 门店开关通过乐观版本并发控制，防止并发覆盖。
3. 顾客端/订单侧对营业状态一律通过 `ShopQuery` 实时读取，不读缓存。

## 相关文档

- [P3_CONTRACT.md](../P3_CONTRACT.md)（门店与目录接口契约）
- 数据库：`V3__catalog_and_shop.sql`
