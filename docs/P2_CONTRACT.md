# P2：商品目录与门店营业

## 前置验收和交付范围

2026-09-18 对 P1 重新执行 `./scripts/verify.sh`：33 项测试通过，Checkstyle 零违规。
检查覆盖员工认证、即时会话撤销、权限、JPA 并发控制、API 与错误协议、审计脱敏，符合 P1 阶段标准。

P2 实现分类、菜品、口味、套餐、上下架、图片上传、公开目录缓存，以及单店资料和营业状态。
沿用 `/api/v1`、Bearer、UUID、UTC、RFC 9457 和必填修改版本。Flutter App 可以匿名浏览菜单
和门店资料；顾客登录、购物车、下单和支付不在本阶段。

## 领域规则

- 分类种类为 DISH 或 SET_MEAL，创建后不改变；同种类分类名称不区分大小写唯一。
- 分类支持排序、启停用，最多 100 个；有在售商品时不能停用，有任意商品引用时不能删除。
- `MenuProduct` 是可售商品聚合，DISH 与 SET_MEAL 共享定价/生命周期，种类创建后不改变。
  菜品有口味组而无套餐组成；套餐有 1—50 个组成菜品而不能直接定义口味组或嵌套套餐。
- 新商品默认 OFF_SALE。编辑资料前先下架；同分类内商品名称不区分大小写唯一。
- 商品分类的种类必须匹配。价格使用 CNY 元，范围 0.01—999999.99，禁止多于两位有效小数和隐式舍入。
- 每个菜品最多 10 个单选口味组，每组 1—20 个选项；组名与选项去空白后不能重复。
- 套餐中同一菜品只出现一次，数量 1—99；套餐提供固定口味选择，必选口味不能漏选，未知值被拒绝。
- 套餐上架时所有组成菜品及其分类必须可售；被在售套餐引用的菜品不能直接下架。
- 被任意套餐引用的菜品不能删除或编辑，先从套餐移除以避免口味选择失效。下架套餐可修改组成。
- 商品删除要求下架且无套餐引用；图片可能被多个商品共享，商品删除不隐式删除图片。
- 单店初始 CLOSED，管理员填好联系电话和地址才能 OPEN。营业时不能清空联系资料。
  门店可售状态与商品上下架是不同概念：打烊时菜单仍可浏览，后续下单必须另外检查营业状态。

## 管理端 API（ADMIN）

| 方法与路径 | 输入要点 | 成功 |
| --- | --- | --- |
| GET `/api/v1/catalog/categories` | 所有分类，按 sortOrder/id 排序 | 200 数组 |
| POST `/api/v1/catalog/categories` | kind、name、sortOrder | 201，资源与 Location |
| GET `/api/v1/catalog/categories/{id}` | UUID | 200 资源 |
| PUT `/api/v1/catalog/categories/{id}` | name、sortOrder、enabled、version | 200 新快照 |
| DELETE `/api/v1/catalog/categories/{id}?version=...` | 版本必填 | 204 |
| GET `/api/v1/catalog/products` | 可选 kind/categoryId；page=0、size=20，最大 100 | 200 分页 |
| POST `/api/v1/catalog/products` | kind 和 details | 201，资源与 Location |
| GET `/api/v1/catalog/products/{id}` | 包含下架商品 | 200 资源 |
| PUT `/api/v1/catalog/products/{id}` | details、version | 200 新快照 |
| PATCH `/api/v1/catalog/products/{id}/status` | status=ON_SALE/OFF_SALE、version | 200 新快照 |
| DELETE `/api/v1/catalog/products/{id}?version=...` | 版本必填 | 204 |
| POST `/api/v1/catalog/images` | multipart/form-data，file 字段 | 201 元数据与 Location |
| GET `/api/v1/catalog/images/{id}` | 查询图片的短期签名地址 | 200 url/expiresAt |
| GET `/api/v1/shop` | 单店资源 | 200 资源 |
| PUT `/api/v1/shop` | name、phone、address、version | 200 新快照 |
| PATCH `/api/v1/shop/status` | status=OPEN/CLOSED、version | 200 新快照 |

STAFF 无目录和店铺管理权限。应用服务通过 `identity :: api` 的 StaffAuthorization 再次检查当前
账号状态、角色和安全版本，不能仅由调用方传入角色字符串。

## Flutter 公开读取 API

| 方法与路径 | 行为 |
| --- | --- |
| GET `/api/v1/menu/categories` | 仅启用分类 |
| GET `/api/v1/menu/products` | 仅在售商品，支持相同筛选和零基分页 |
| GET `/api/v1/menu/products/{id}` | 不存在/下架/分类停用返回 404 |
| GET `/api/v1/menu/images/{id}` | 仅当图片被在售商品引用时返回临时地址 |
| GET `/api/v1/storefront` | 门店名称、电话、地址、OPEN/CLOSED、版本 |

公开列表可缓存，后续下单和购物车通过 `catalog :: api` 的 CatalogQuery 读取实时可售快照，不能
信任 App 提交的名称、价格或过去缓存的销售状态。shop 同样提供 `shop :: api` 实时查询契约。

## 商品请求示例

菜品创建，图片可先上传后填入 imageId，也可暂时为 null：

```json
{
  "kind": "DISH",
  "details": {
    "categoryId": "分类UUID",
    "name": "番茄鸡蛋面",
    "description": "现做面食",
    "price": 18.50,
    "imageId": null,
    "flavors": [
      {"name": "辣度", "options": ["不辣", "微辣"], "required": true}
    ],
    "components": []
  }
}
```

套餐创建使用 SET_MEAL 分类，flavors 为空：

```json
{
  "kind": "SET_MEAL",
  "details": {
    "categoryId": "套餐分类UUID",
    "name": "双人面食套餐",
    "description": "两份微辣面食",
    "price": 35.00,
    "imageId": null,
    "flavors": [],
    "components": [
      {"dishId": "菜品UUID", "quantity": 2, "selections": {"辣度": "微辣"}}
    ]
  }
}
```

PUT 传 `{ "details": {...}, "version": 读取到的版本 }`。状态变更也必须带最新 version；响应为
当前快照。不改变状态/资料的幂等写入可能不递增实体版本，客户端应始终读取响应中的版本。
分页固定为 items/page/size/totalElements/totalPages，不返回 ORM 类型。

业务错误码前缀 CATALOG_ / SHOP_，分类包括 INVALID_INPUT、NOT_FOUND、CONFLICT、VERSION_CONFLICT、
UNAVAILABLE（按模块适用）。唯一约束冲突沿用 DATA_CONFLICT；ORM 并发冲突沿用 VERSION_CONFLICT。
错误内容不包含 SQL、存储凭证或签名参数。文件大小超过 Servlet 限制时返回 413。

## 并发与缓存策略

目录后台是低频管理写入，所有分类/商品写入先在当前事务锁定 catalog_revision 的单行，再校验
分类引用、套餐组成和实体版本。锁持有到事务结束；上架套餐与下架菜品竞争时只允许一个合法结果提交。
这避免仅对单个聚合使用乐观锁时产生跨聚合写偏差。该方案面向当前单店，未来大规模多店可按店拆锁。

成功写入同时递增修订号；回滚同时回滚修订号。公开列表读取一次数据库修订号，然后使用该代际
加筛选、分页组成 Redis 键。新提交后的读取自然使用新键，旧在途查询即使回填也只能写入旧代际。
缓存 TTL 为两分钟，旧键自动过期，不执行全库 KEYS 或依赖不可靠的提交后删除。

Redis 不可用时公开目录回源 PostgreSQL，身份登录限流仍然保持失败拒绝，两者安全语义不同。
缓存只保存公开 DTO，不保存签名 URL；反序列化失败或 null 缓存都回源。门店营业状态直接读数据库。

JPA 分页先读取本页商品，再用实体图加载本页套餐明细，避免集合 fetch join 与分页结合造成
内存分页或截断。查询数量测试验证不随本页商品数量增长；缓存命中仅查询目录修订号。

## 图片与对象存储

- AWS SDK for Java v2 2.55.0 对接现有 RustFS 1.0.0，采用 path-style，服务端凭证来自环境。
- 上传仅支持真实 PNG/JPEG，最大 5 MiB；实际解码验证，边长最多 4096，像素总量最多 1600 万。
- 文件名、扩展名和浏览器 Content-Type 不决定存储类型，服务端生成唯一对象键。
- 元数据存数据库，私有桶存对象；图片 GET 返回五分钟签名链接，未签名访问仍被拒绝。
- 公共链接只发给已被在售商品引用的图片；已发出的链接在五分钟内仍可能有效，不承诺瞬时撤回。
- 存储调用不在数据库事务内，元数据提交失败会补偿删除本次对象。若进程在两个步骤间崩溃或
  补偿也失败，可能留下孤立对象，固定日志记录 imageId 供清理；当前未增加后台自动垃圾回收任务。
- 上传暂未绑定商品的图片可保留供随后编辑选择；当前没有图片管理删除接口，避免误删共享图片。

`RUSTFS_ENDPOINT` 是服务端连接地址，`RUSTFS_PUBLIC_ENDPOINT` 可指定同一存储服务的客户端可达
地址用于签名。Flutter 真机不能访问开发机的 127.0.0.1；联调时需配置可达地址及受控代理/端口。
这里不自动把当前只监听回环的 RustFS 暴露到局域网。

## 数据迁移、测试与运行

新增 V3 创建目录及门店表，不修改 V1/V2，不清空已有身份数据。门店初始记录由迁移提供，默认打烊。
开发桶为 han-menu；集成测试只使用 han-menu-test 的随机前缀，结束后删除本次对象，不清空整个桶。
测试会在专用测试桶不存在时创建它。CI 增加 RustFS 服务和测试凭证，不读取真实开发凭证。

```bash
python3 scripts/middleware.py up
./scripts/verify.sh
./scripts/with-env.sh ./mvnw spring-boot:run
```

完整测试保留 P1，追加价格/规格/状态的领域测试、HTTP 权限、分类与套餐引用、跨事务并发、缓存
命中/回滚/损坏、Redis 失联降级、S3 失联失败、实际图片私有读写及补偿清理。运行接口见 Swagger。
当前范围不含营业时间段、配送范围/费用、库存、顾客身份、购物车、支付或 Flutter 客户端实现。

本地验收结果：53 项测试通过，Checkstyle 零违规；打包应用已验证 V3 升级保留 P1 账号、
公开及管理查询、共 30 个 OpenAPI 操作，以及真实 HTTP 上传超限返回 413。
远程 CI 尚未运行，不能将本地验证视为已完成远程部署。
