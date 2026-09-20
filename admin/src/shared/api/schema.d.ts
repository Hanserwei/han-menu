// 自动生成：依据 contracts/openapi.json，禁止手工修改。
export interface paths {
    "/api/v1/shop": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 读取门店配置 */
        get: operations["get"];
        /** 修改门店名称、电话和地址 */
        put: operations["revise"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/notifications/receipt": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 读取本人通知阅读游标 */
        get: operations["receipt"];
        /** 按版本前移本人阅读游标 */
        put: operations["acknowledge"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/me/password": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /**
         * 修改本人密码
         * @description 成功后该账号的全部旧令牌失效，必须重新创建会话
         */
        put: operations["password"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/employees/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 读取员工资源 */
        get: operations["get_1"];
        /**
         * 更新员工资料
         * @description 必须提供当前 version；不会修改角色、状态或密码
         */
        put: operations["update"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/customer/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 读取本人资料 */
        get: operations["me"];
        /** 修改本人昵称 */
        put: operations["updateMe"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/customer/me/password": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** 修改本人密码 */
        put: operations["password_1"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/customer/addresses/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 读取自己的地址 */
        get: operations["get_2"];
        /** 修改收货地址 */
        put: operations["updateAddress"];
        post?: never;
        /** 删除收货地址 */
        delete: operations["deleteAddress"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/catalog/products/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 读取商品详情 */
        get: operations["product"];
        /** 编辑下架商品 */
        put: operations["update_1"];
        post?: never;
        /** 删除下架且无引用的商品 */
        delete: operations["delete"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/catalog/categories/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 读取分类 */
        get: operations["category"];
        /** 更新分类和启停用状态 */
        put: operations["updateCategory"];
        post?: never;
        /** 删除无引用分类 */
        delete: operations["deleteCategory"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/sessions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 创建员工会话
         * @description 返回的 accessToken 只在本次响应中可见
         */
        post: operations["create"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/reports/projection/rebuild": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 从业务模块公开快照原子重建统计投影 */
        post: operations["rebuild"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/payments/{id}/refresh": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 主动核对本人支付状态 */
        post: operations["refresh"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/payment-notifications/alipay": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 接收并验签支付宝沙箱通知 */
        post: operations["notify"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/orders": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 分页查询本人订单历史 */
        get: operations["history"];
        put?: never;
        /** 幂等提交待付款订单 */
        post: operations["submit"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/orders/{id}/reorder": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 按当前目录再来一单到购物车 */
        post: operations["reorder"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/orders/{id}/reminders": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 催促本人已付款且未完成的订单 */
        post: operations["remind"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/orders/{id}/payments": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 幂等创建订单支付宝沙箱 App 支付意图 */
        post: operations["create_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/orders/{id}/cancellation": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 取消本人待付款或待接单订单 */
        post: operations["cancel"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/notifications/{id}/redelivery": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 管理员重试投递耗尽的通知 */
        post: operations["retry"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/notifications/stream-tickets": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 签发三十秒一次性通知连接票据 */
        post: operations["ticket"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/management/orders/{id}/rejection": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 拒绝已付款订单并申请全额退款 */
        post: operations["reject"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/management/orders/{id}/delivery": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 开始配送已接单订单 */
        post: operations["deliver"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/management/orders/{id}/completion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 完成配送中的订单 */
        post: operations["complete"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/management/orders/{id}/cancellation": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 取消接单前或配送前订单并申请全额退款 */
        post: operations["cancel_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/management/orders/{id}/acceptance": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 接收已付款订单 */
        post: operations["accept"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/employees": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查询员工列表
         * @description page 从 0 开始；size 默认 20，最大 100
         */
        get: operations["search"];
        put?: never;
        /**
         * 创建员工
         * @description 初始密码必须提供；角色由服务端固定为 STAFF
         */
        post: operations["create_2"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/customer/sessions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 顾客登录 */
        post: operations["login"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/customer/addresses": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 读取自己的地址簿 */
        get: operations["list"];
        put?: never;
        /** 创建收货地址 */
        post: operations["createAddress"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/customer/accounts": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 注册顾客
         * @description 手机号作为登录标识，当前没有短信验证，不代表已验证手机号归属
         */
        post: operations["register"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/catalog/products": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 后台商品分页，包括下架商品 */
        get: operations["products"];
        put?: never;
        /** 创建下架菜品或套餐 */
        post: operations["create_3"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/catalog/images": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 上传 PNG 或 JPEG 商品图片
         * @description 最多 5 MiB，校验实际格式和像素尺寸
         */
        post: operations["upload"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/catalog/categories": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询全部分类 */
        get: operations["categories"];
        put?: never;
        /** 创建分类 */
        post: operations["createCategory"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/cart/items": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 添加商品并合并同规格条目 */
        post: operations["add"];
        /** 清空本人购物车 */
        delete: operations["clear"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/shop/status": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /** 开店或打烊 */
        patch: operations["status"];
        trace?: never;
    };
    "/api/v1/management/customers/{id}/status": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /**
         * 启停用顾客账号
         * @description 要求当前 version；重新启用不会恢复旧会话
         */
        patch: operations["changeStatus"];
        trace?: never;
    };
    "/api/v1/employees/{id}/status": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /**
         * 变更员工状态
         * @description 停用撤销旧会话；重新启用不会使旧会话复活
         */
        patch: operations["status_1"];
        trace?: never;
    };
    "/api/v1/customer/addresses/{id}/default": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /** 设置默认地址 */
        patch: operations["makeDefault"];
        trace?: never;
    };
    "/api/v1/catalog/products/{id}/status": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /** 商品上下架，校验分类与套餐组成 */
        patch: operations["status_2"];
        trace?: never;
    };
    "/api/v1/cart/items/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /** 删除条目 */
        delete: operations["remove"];
        options?: never;
        head?: never;
        /** 修改条目数量 */
        patch: operations["quantity"];
        trace?: never;
    };
    "/api/v1/workspace": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 员工工作台待办与当前门店目录摘要 */
        get: operations["workspace"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/storefront": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 获取门店当前营业状态和联系信息 */
        get: operations["current"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/reports/sales": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询已完成订单商品销量排行 */
        get: operations["sales"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/reports/reconciliation": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询收退款金额与投影对账差异 */
        get: operations["reconciliation"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/reports/projection": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询统计投影代际、版本与记录数 */
        get: operations["projection"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/reports/operations": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询经营汇总和每日统计 */
        get: operations["operations"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/reports/export": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 导出经营日账、销量和资金对账 XLSX */
        get: operations["export"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/refunds/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询本人退款状态 */
        get: operations["refund"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/payments/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询本人支付状态 */
        get: operations["get_3"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/orders/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询本人订单详情 */
        get: operations["detail"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/notifications": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 按游标补查来单和催单通知 */
        get: operations["feed"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/notifications/{id}/attempts": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询通知投递轨迹 */
        get: operations["attempts"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/menu/products": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 读取在售商品分页 */
        get: operations["products_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/menu/products/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 读取可售商品详情 */
        get: operations["product_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/menu/images/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 获取在售商品图片的临时读取地址 */
        get: operations["image"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/menu/categories": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 读取启用的分类 */
        get: operations["categories_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 读取自己的身份 */
        get: operations["get_4"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/management/refunds": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 分页查询退款流水 */
        get: operations["refunds"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/management/refunds/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 读取退款流水详情 */
        get: operations["refund_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/management/payments": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 分页查询支付流水 */
        get: operations["payments"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/management/payments/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 读取支付流水详情 */
        get: operations["payment"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/management/orders": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 分页查询待处理或历史订单 */
        get: operations["history_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/management/orders/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 读取履约订单详情 */
        get: operations["detail_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/management/customers": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 分页检索顾客档案 */
        get: operations["search_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/management/customers/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 读取顾客档案 */
        get: operations["get_5"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/management/audit-events": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 分页检索身份安全审计 */
        get: operations["search_2"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/catalog/images/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 获取私有图片的五分钟签名读取地址 */
        get: operations["image_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/cart": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 读取本人购物车 */
        get: operations["get_6"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/sessions/current": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /**
         * 撤销当前会话
         * @description 当前令牌立即失效，其他会话不受影响
         */
        delete: operations["delete_1"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/customer/sessions/current": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /** 顾客退出 */
        delete: operations["logout"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        Profile: {
            name: string;
            phone: string;
            address: string;
            /** Format: int64 */
            version: number;
        };
        ShopView: {
            name?: string;
            phone?: string;
            address?: string;
            status?: string;
            /** Format: int64 */
            version?: number;
        };
        Acknowledge: {
            /** Format: int64 */
            sequence: number;
            /** Format: int64 */
            version: number;
        };
        ReceiptView: {
            /** Format: int64 */
            sequence?: number;
            /** Format: int64 */
            version?: number;
            /** Format: date-time */
            updatedAt?: string;
        };
        PasswordChange: {
            currentPassword: string;
            newPassword: string;
        };
        UpdateEmployee: {
            username: string;
            displayName: string;
            phone?: string;
            /** Format: int64 */
            version: number;
        };
        ProfileUpdate: {
            displayName: string;
            /** Format: int64 */
            version: number;
        };
        CustomerView: {
            /** Format: uuid */
            id?: string;
            phone?: string;
            displayName?: string;
            /** Format: int64 */
            version?: number;
        };
        AddressRequest: {
            label: string;
            recipientName: string;
            phone: string;
            province: string;
            city: string;
            district: string;
            detail: string;
            defaultAddress?: boolean;
        };
        AddressUpdate: {
            address: components["schemas"]["AddressRequest"];
            /** Format: int64 */
            version: number;
        };
        AddressView: {
            /** Format: uuid */
            id?: string;
            label?: string;
            recipientName?: string;
            phone?: string;
            province?: string;
            city?: string;
            district?: string;
            detail?: string;
            defaultAddress?: boolean;
            /** Format: int64 */
            version?: number;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            updatedAt?: string;
        };
        ComponentInput: {
            /** Format: uuid */
            dishId: string;
            /** Format: int32 */
            quantity?: number;
            selections: {
                [key: string]: string;
            };
        };
        FlavorInput: {
            name: string;
            options: string[];
            required?: boolean;
        };
        ProductDetails: {
            /** Format: uuid */
            categoryId: string;
            name: string;
            description: string;
            price: number;
            /** Format: uuid */
            imageId?: string;
            flavors: components["schemas"]["FlavorInput"][];
            components: components["schemas"]["ComponentInput"][];
        };
        ProductUpdate: {
            details: components["schemas"]["ProductDetails"];
            /** Format: int64 */
            version: number;
        };
        ComponentView: {
            /** Format: uuid */
            dishId?: string;
            /** Format: int32 */
            quantity?: number;
            selections?: {
                [key: string]: string;
            };
        };
        FlavorView: {
            name?: string;
            options?: string[];
            required?: boolean;
        };
        ProductView: {
            /** Format: uuid */
            id?: string;
            kind?: string;
            /** Format: uuid */
            categoryId?: string;
            name?: string;
            description?: string;
            price?: number;
            currency?: string;
            /** Format: uuid */
            imageId?: string;
            flavors?: components["schemas"]["FlavorView"][];
            components?: components["schemas"]["ComponentView"][];
            status?: string;
            /** Format: int64 */
            version?: number;
            /** Format: date-time */
            createdAt?: string;
        };
        CategoryUpdate: {
            name: string;
            /** Format: int32 */
            sortOrder?: number;
            enabled: boolean;
            /** Format: int64 */
            version: number;
        };
        CategoryView: {
            /** Format: uuid */
            id?: string;
            kind?: string;
            name?: string;
            /** Format: int32 */
            sortOrder?: number;
            enabled?: boolean;
            /** Format: int64 */
            version?: number;
        };
        Credentials: {
            username: string;
            password: string;
        };
        SessionView: {
            accessToken?: string;
            tokenType?: string;
            /** Format: date-time */
            expiresAt?: string;
        };
        Rebuild: {
            /** Format: int64 */
            version: number;
        };
        ProjectionStatus: {
            /** Format: int64 */
            version?: number;
            /** Format: int64 */
            generation?: number;
            /** Format: int64 */
            revision?: number;
            initialized?: boolean;
            /** Format: date-time */
            updatedAt?: string;
            /** Format: date-time */
            rebuiltAt?: string;
            /** Format: int64 */
            orders?: number;
            /** Format: int64 */
            customers?: number;
            /** Format: int64 */
            receipts?: number;
            /** Format: int64 */
            refunds?: number;
        };
        Refresh: {
            /** Format: int64 */
            version: number;
        };
        PaymentView: {
            /** Format: uuid */
            id?: string;
            /** Format: uuid */
            orderId?: string;
            status?: string;
            amount?: number;
            currency?: string;
            /** Format: date-time */
            expiresAt?: string;
            /** Format: date-time */
            paidAt?: string;
            closeRequested?: boolean;
            /** Format: int64 */
            version?: number;
            /** Format: uuid */
            refundId?: string;
            lastFailure?: string;
        };
        MultiValueMapStringString: {
            all?: {
                [key: string]: string;
            };
            empty?: boolean;
        } & {
            [key: string]: string[];
        };
        Submit: {
            /** Format: uuid */
            addressId: string;
            /** Format: int64 */
            addressVersion: number;
            /** Format: int64 */
            cartVersion: number;
            itemIds: string[];
        };
        Address: {
            /** Format: uuid */
            sourceId?: string;
            /** Format: int64 */
            sourceVersion?: number;
            recipientName?: string;
            phone?: string;
            province?: string;
            city?: string;
            district?: string;
            detail?: string;
        };
        Component: {
            /** Format: uuid */
            productId?: string;
            name?: string;
            /** Format: int32 */
            quantity?: number;
            selections?: {
                [key: string]: string;
            };
        };
        Detail: {
            /** Format: uuid */
            id?: string;
            status?: string;
            /** Format: int64 */
            version?: number;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            cancelledAt?: string;
            address?: components["schemas"]["Address"];
            items?: components["schemas"]["Line"][];
            total?: number;
            currency?: string;
            /** Format: date-time */
            expiresAt?: string;
            lifecycle?: components["schemas"]["Lifecycle"];
            /** Format: int32 */
            reminderCount?: number;
            /** Format: date-time */
            lastRemindedAt?: string;
        };
        Lifecycle: {
            /** Format: uuid */
            paymentId?: string;
            /** Format: date-time */
            paidAt?: string;
            /** Format: date-time */
            acceptedAt?: string;
            /** Format: date-time */
            deliveredAt?: string;
            /** Format: date-time */
            completedAt?: string;
            cancelReason?: string;
            refundStatus?: string;
            /** Format: uuid */
            refundId?: string;
        };
        Line: {
            /** Format: uuid */
            id?: string;
            /** Format: uuid */
            productId?: string;
            kind?: string;
            name?: string;
            unitPrice?: number;
            /** Format: int32 */
            quantity?: number;
            selections?: {
                [key: string]: string;
            };
            components?: components["schemas"]["Component"][];
            subtotal?: number;
        };
        Reorder: {
            /** Format: int64 */
            version: number;
            /** Format: int64 */
            cartVersion: number;
        };
        Reordered: {
            /** Format: int64 */
            cartVersion?: number;
        };
        Reminder: {
            /** Format: int64 */
            version: number;
        };
        Create: {
            /** Format: int64 */
            version: number;
        };
        AppPayment: {
            /** Format: uuid */
            id?: string;
            status?: string;
            amount?: number;
            currency?: string;
            /** Format: date-time */
            expiresAt?: string;
            /** Format: int64 */
            version?: number;
            channel?: string;
            invocation?: string;
            orderString?: string;
        };
        Cancellation: {
            /** Format: int64 */
            version: number;
        };
        Version: {
            /** Format: int64 */
            version: number;
        };
        NoticeView: {
            /** Format: uuid */
            id?: string;
            /** Format: int64 */
            sequence?: number;
            /** Format: uuid */
            orderId?: string;
            type?: string;
            /** Format: date-time */
            occurredAt?: string;
            deliveryStatus?: string;
            /** Format: int32 */
            attempts?: number;
            lastFailure?: string;
            /** Format: int64 */
            version?: number;
        };
        Ticket: {
            ticket?: string;
            /** Format: date-time */
            expiresAt?: string;
            protocol?: string;
        };
        CreateEmployee: {
            username: string;
            displayName: string;
            phone?: string;
            password: string;
        };
        EmployeeView: {
            /** Format: uuid */
            id?: string;
            username?: string;
            displayName?: string;
            phone?: string;
            role?: string;
            /** @enum {string} */
            status?: "ACTIVE" | "DISABLED";
            /** Format: int64 */
            version?: number;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            updatedAt?: string;
        };
        LoginRequest: {
            phone: string;
            password: string;
        };
        RegisterRequest: {
            phone: string;
            displayName: string;
            password: string;
        };
        ProductCreate: {
            /** @enum {string} */
            kind: "DISH" | "SET_MEAL";
            details: components["schemas"]["ProductDetails"];
        };
        ImageView: {
            /** Format: uuid */
            id?: string;
            mediaType?: string;
            /** Format: int64 */
            size?: number;
            /** Format: date-time */
            createdAt?: string;
        };
        CategoryCreate: {
            /** @enum {string} */
            kind: "DISH" | "SET_MEAL";
            name: string;
            /** Format: int32 */
            sortOrder?: number;
        };
        AddItem: {
            /** Format: uuid */
            productId: string;
            /** Format: int32 */
            quantity?: number;
            selections: {
                [key: string]: string;
            };
            /** Format: int64 */
            version: number;
        };
        CartView: {
            items?: components["schemas"]["ItemView"][];
            /** Format: int64 */
            version?: number;
            /** Format: date-time */
            updatedAt?: string;
            estimatedTotal?: number;
            currency?: string;
        };
        ItemView: {
            /** Format: uuid */
            id?: string;
            /** Format: uuid */
            productId?: string;
            kind?: string;
            name?: string;
            unitPrice?: number;
            /** Format: int32 */
            quantity?: number;
            selections?: {
                [key: string]: string;
            };
            available?: boolean;
            unavailableReason?: string;
            subtotal?: number;
        };
        ShopStatusChange: {
            /** @enum {string} */
            status: "OPEN" | "CLOSED";
            /** Format: int64 */
            version: number;
        };
        CustomerStatusChange: {
            enabled: boolean;
            /** Format: int64 */
            version: number;
        };
        ManagedCustomerView: {
            /** Format: uuid */
            id?: string;
            phone?: string;
            displayName?: string;
            enabled?: boolean;
            /** Format: int64 */
            version?: number;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            updatedAt?: string;
        };
        EmployeeStatusChange: {
            /** @enum {string} */
            status: "ACTIVE" | "DISABLED";
            /** Format: int64 */
            version: number;
        };
        SaleChange: {
            /** @enum {string} */
            status: "ON_SALE" | "OFF_SALE";
            /** Format: int64 */
            version: number;
        };
        QuantityChange: {
            /** Format: int32 */
            quantity?: number;
            /** Format: int64 */
            version: number;
        };
        Metadata: {
            /** Format: int64 */
            version?: number;
            /** Format: int64 */
            generation?: number;
            /** Format: int64 */
            revision?: number;
            /** Format: date-time */
            updatedAt?: string;
            /** Format: date-time */
            rebuiltAt?: string;
        };
        Workspace: {
            /** Format: date */
            businessDate?: string;
            zone?: string;
            shopStatus?: string;
            projection?: components["schemas"]["Metadata"];
            /** Format: int64 */
            awaitingAcceptance?: number;
            /** Format: int64 */
            accepted?: number;
            /** Format: int64 */
            delivering?: number;
            /** Format: int64 */
            cancelling?: number;
            /** Format: int64 */
            refunding?: number;
            /** Format: int64 */
            createdToday?: number;
            /** Format: int64 */
            completedToday?: number;
            /** Format: int64 */
            dishesOnSale?: number;
            /** Format: int64 */
            dishesOffSale?: number;
            /** Format: int64 */
            mealsOnSale?: number;
            /** Format: int64 */
            mealsOffSale?: number;
        };
        Sale: {
            /** Format: uuid */
            productId?: string;
            kind?: string;
            name?: string;
            /** Format: int64 */
            quantity?: number;
            amount?: number;
        };
        Sales: {
            /** Format: date */
            from?: string;
            /** Format: date */
            to?: string;
            currency?: string;
            projection?: components["schemas"]["Metadata"];
            items?: components["schemas"]["Sale"][];
        };
        Reconciliation: {
            /** Format: date */
            from?: string;
            /** Format: date */
            to?: string;
            currency?: string;
            projection?: components["schemas"]["Metadata"];
            receivedAmount?: number;
            refundedAmount?: number;
            netReceivedAmount?: number;
            /** Format: int64 */
            missingOrders?: number;
            /** Format: int64 */
            paymentMismatches?: number;
            /** Format: int64 */
            missingPayments?: number;
            /** Format: int64 */
            refundMismatches?: number;
            /** Format: int64 */
            pendingRefunds?: number;
            /** Format: int64 */
            ordersMissingReceipts?: number;
            /** Format: int64 */
            ordersMissingRefunds?: number;
        };
        Day: {
            /** Format: date */
            date?: string;
            /** Format: int64 */
            submittedOrders?: number;
            /** Format: int64 */
            completedCohort?: number;
            /** Format: int64 */
            cancelledCohort?: number;
            /** Format: int64 */
            completedOrders?: number;
            turnover?: number;
            receivedAmount?: number;
            refundedAmount?: number;
            netReceivedAmount?: number;
            /** Format: int64 */
            newCustomers?: number;
            /** Format: int64 */
            cumulativeCustomers?: number;
            completionRatePercent?: number;
        };
        Operations: {
            /** Format: date */
            from?: string;
            /** Format: date */
            to?: string;
            zone?: string;
            currency?: string;
            projection?: components["schemas"]["Metadata"];
            summary?: components["schemas"]["Summary"];
            days?: components["schemas"]["Day"][];
        };
        Summary: {
            /** Format: int64 */
            submittedOrders?: number;
            /** Format: int64 */
            completedCohort?: number;
            /** Format: int64 */
            cancelledCohort?: number;
            /** Format: int64 */
            completedOrders?: number;
            turnover?: number;
            receivedAmount?: number;
            refundedAmount?: number;
            netReceivedAmount?: number;
            /** Format: int64 */
            newCustomers?: number;
            /** Format: int64 */
            totalCustomers?: number;
            completionRatePercent?: number;
            averageOrderValue?: number;
        };
        RefundView: {
            /** Format: uuid */
            id?: string;
            /** Format: uuid */
            paymentId?: string;
            /** Format: uuid */
            orderId?: string;
            amount?: number;
            currency?: string;
            status?: string;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            confirmedAt?: string;
            /** Format: int64 */
            version?: number;
            lastFailure?: string;
        };
        History: {
            items?: components["schemas"]["Summary"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int64 */
            totalPages?: number;
        };
        Feed: {
            items?: components["schemas"]["NoticeView"][];
            /** Format: int64 */
            nextCursor?: number;
            hasMore?: boolean;
        };
        AttemptView: {
            /** Format: uuid */
            id?: string;
            /** Format: date-time */
            startedAt?: string;
            /** Format: date-time */
            finishedAt?: string;
            status?: string;
            /** Format: int32 */
            sent?: number;
            /** Format: int32 */
            failed?: number;
            failure?: string;
        };
        ProductPage: {
            items?: components["schemas"]["ProductView"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int64 */
            totalPages?: number;
        };
        ImageLink: {
            /** Format: uri */
            url?: string;
            /** Format: date-time */
            expiresAt?: string;
        };
        CurrentEmployeeView: {
            /** Format: uuid */
            id?: string;
            username?: string;
            displayName?: string;
            role?: string;
        };
        ManagedRefundView: {
            /** Format: uuid */
            id?: string;
            /** Format: uuid */
            paymentId?: string;
            /** Format: uuid */
            orderId?: string;
            /** Format: uuid */
            customerId?: string;
            amount?: number;
            currency?: string;
            /** @enum {string} */
            status?: "PENDING" | "SUCCEEDED";
            tradeNo?: string;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            confirmedAt?: string;
            /** Format: date-time */
            nextAttemptAt?: string;
            lastFailure?: string;
            /** Format: int64 */
            version?: number;
        };
        TransactionPageViewManagedRefundView: {
            items?: components["schemas"]["ManagedRefundView"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int64 */
            totalPages?: number;
        };
        ManagedPaymentView: {
            /** Format: uuid */
            id?: string;
            /** Format: uuid */
            orderId?: string;
            /** Format: uuid */
            customerId?: string;
            amount?: number;
            currency?: string;
            /** @enum {string} */
            status?: "PENDING" | "SUCCEEDED" | "CLOSED";
            tradeNo?: string;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            expiresAt?: string;
            /** Format: date-time */
            paidAt?: string;
            closeRequested?: boolean;
            /** Format: date-time */
            nextAttemptAt?: string;
            lastFailure?: string;
            /** Format: int64 */
            version?: number;
        };
        TransactionPageViewManagedPaymentView: {
            items?: components["schemas"]["ManagedPaymentView"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int64 */
            totalPages?: number;
        };
        CustomerPageView: {
            items?: components["schemas"]["ManagedCustomerView"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int64 */
            totalPages?: number;
        };
        AuditEntryView: {
            /** Format: uuid */
            id?: string;
            action?: string;
            /** Format: uuid */
            actorId?: string;
            /** Format: uuid */
            subjectId?: string;
            successful?: boolean;
            /** Format: date-time */
            occurredAt?: string;
        };
        AuditPageView: {
            items?: components["schemas"]["AuditEntryView"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int64 */
            totalPages?: number;
        };
        EmployeePageView: {
            items?: components["schemas"]["EmployeeView"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int64 */
            totalPages?: number;
        };
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    get: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ShopView"];
                };
            };
        };
    };
    revise: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Profile"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ShopView"];
                };
            };
        };
    };
    receipt: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ReceiptView"];
                };
            };
        };
    };
    acknowledge: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Acknowledge"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ReceiptView"];
                };
            };
        };
    };
    password: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PasswordChange"];
            };
        };
        responses: {
            /** @description 密码修改成功，旧会话已失效 */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    get_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EmployeeView"];
                };
            };
        };
    };
    update: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateEmployee"];
            };
        };
        responses: {
            /** @description 资料更新成功 */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    me: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CustomerView"];
                };
            };
        };
    };
    updateMe: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ProfileUpdate"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CustomerView"];
                };
            };
        };
    };
    password_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PasswordChange"];
            };
        };
        responses: {
            /** @description 密码修改成功 */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    get_2: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AddressView"];
                };
            };
        };
    };
    updateAddress: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AddressUpdate"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AddressView"];
                };
            };
        };
    };
    deleteAddress: {
        parameters: {
            query: {
                version: number;
            };
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 地址已删除 */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    product: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ProductView"];
                };
            };
        };
    };
    update_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ProductUpdate"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ProductView"];
                };
            };
        };
    };
    delete: {
        parameters: {
            query: {
                version: number;
            };
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 商品删除成功 */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    category: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CategoryView"];
                };
            };
        };
    };
    updateCategory: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CategoryUpdate"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CategoryView"];
                };
            };
        };
    };
    deleteCategory: {
        parameters: {
            query: {
                version: number;
            };
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 分类删除成功 */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    create: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Credentials"];
            };
        };
        responses: {
            /** @description 资源创建成功 */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SessionView"];
                };
            };
        };
    };
    rebuild: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Rebuild"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ProjectionStatus"];
                };
            };
        };
    };
    refresh: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Refresh"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PaymentView"];
                };
            };
        };
    };
    notify: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/x-www-form-urlencoded": {
                    parameters: components["schemas"]["MultiValueMapStringString"];
                };
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "text/plain": string;
                };
            };
        };
    };
    history: {
        parameters: {
            query?: {
                page?: number;
                size?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["History"];
                };
            };
        };
    };
    submit: {
        parameters: {
            query?: never;
            header: {
                "Idempotency-Key": string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Submit"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Detail"];
                };
            };
        };
    };
    reorder: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Reorder"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Reordered"];
                };
            };
        };
    };
    remind: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Reminder"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Detail"];
                };
            };
        };
    };
    create_1: {
        parameters: {
            query?: never;
            header: {
                "Idempotency-Key": string;
            };
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Create"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AppPayment"];
                };
            };
        };
    };
    cancel: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Cancellation"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Detail"];
                };
            };
        };
    };
    retry: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Version"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["NoticeView"];
                };
            };
        };
    };
    ticket: {
        parameters: {
            query?: never;
            header: {
                Authorization: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Ticket"];
                };
            };
        };
    };
    reject: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Version"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Detail"];
                };
            };
        };
    };
    deliver: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Version"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Detail"];
                };
            };
        };
    };
    complete: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Version"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Detail"];
                };
            };
        };
    };
    cancel_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Version"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Detail"];
                };
            };
        };
    };
    accept: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Version"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Detail"];
                };
            };
        };
    };
    search: {
        parameters: {
            query?: {
                page?: number;
                size?: number;
                name?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EmployeePageView"];
                };
            };
        };
    };
    create_2: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateEmployee"];
            };
        };
        responses: {
            /** @description 资源创建成功 */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EmployeeView"];
                };
            };
        };
    };
    login: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["LoginRequest"];
            };
        };
        responses: {
            /** @description 顾客会话已创建 */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SessionView"];
                };
            };
        };
    };
    list: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AddressView"][];
                };
            };
        };
    };
    createAddress: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AddressRequest"];
            };
        };
        responses: {
            /** @description 地址已创建 */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AddressView"];
                };
            };
        };
    };
    register: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RegisterRequest"];
            };
        };
        responses: {
            /** @description 顾客账号已创建 */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CustomerView"];
                };
            };
        };
    };
    products: {
        parameters: {
            query?: {
                kind?: "DISH" | "SET_MEAL";
                categoryId?: string;
                page?: number;
                size?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ProductPage"];
                };
            };
        };
    };
    create_3: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ProductCreate"];
            };
        };
        responses: {
            /** @description 商品创建成功 */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ProductView"];
                };
            };
        };
    };
    upload: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "multipart/form-data": {
                    /** Format: binary */
                    file: string;
                };
            };
        };
        responses: {
            /** @description 图片创建成功 */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ImageView"];
                };
            };
        };
    };
    categories: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CategoryView"][];
                };
            };
        };
    };
    createCategory: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CategoryCreate"];
            };
        };
        responses: {
            /** @description 分类创建成功 */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CategoryView"];
                };
            };
        };
    };
    add: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AddItem"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CartView"];
                };
            };
        };
    };
    clear: {
        parameters: {
            query: {
                version: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CartView"];
                };
            };
        };
    };
    status: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ShopStatusChange"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ShopView"];
                };
            };
        };
    };
    changeStatus: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CustomerStatusChange"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ManagedCustomerView"];
                };
            };
        };
    };
    status_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["EmployeeStatusChange"];
            };
        };
        responses: {
            /** @description 状态更新成功 */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    makeDefault: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Version"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AddressView"];
                };
            };
        };
    };
    status_2: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SaleChange"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ProductView"];
                };
            };
        };
    };
    remove: {
        parameters: {
            query: {
                version: number;
            };
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CartView"];
                };
            };
        };
    };
    quantity: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["QuantityChange"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CartView"];
                };
            };
        };
    };
    workspace: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Workspace"];
                };
            };
        };
    };
    current: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ShopView"];
                };
            };
        };
    };
    sales: {
        parameters: {
            query: {
                from: string;
                to: string;
                limit?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Sales"];
                };
            };
        };
    };
    reconciliation: {
        parameters: {
            query: {
                from: string;
                to: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Reconciliation"];
                };
            };
        };
    };
    projection: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ProjectionStatus"];
                };
            };
        };
    };
    operations: {
        parameters: {
            query: {
                from: string;
                to: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Operations"];
                };
            };
        };
    };
    export: {
        parameters: {
            query: {
                from: string;
                to: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": string;
                };
            };
        };
    };
    refund: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["RefundView"];
                };
            };
        };
    };
    get_3: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PaymentView"];
                };
            };
        };
    };
    detail: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Detail"];
                };
            };
        };
    };
    feed: {
        parameters: {
            query?: {
                after?: number;
                limit?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Feed"];
                };
            };
        };
    };
    attempts: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AttemptView"][];
                };
            };
        };
    };
    products_1: {
        parameters: {
            query?: {
                kind?: "DISH" | "SET_MEAL";
                categoryId?: string;
                page?: number;
                size?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ProductPage"];
                };
            };
        };
    };
    product_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ProductView"];
                };
            };
        };
    };
    image: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ImageLink"];
                };
            };
        };
    };
    categories_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CategoryView"][];
                };
            };
        };
    };
    get_4: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CurrentEmployeeView"];
                };
            };
        };
    };
    refunds: {
        parameters: {
            query?: {
                status?: "PENDING" | "SUCCEEDED";
                orderId?: string;
                customerId?: string;
                paymentId?: string;
                from?: string;
                to?: string;
                page?: number;
                size?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TransactionPageViewManagedRefundView"];
                };
            };
        };
    };
    refund_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ManagedRefundView"];
                };
            };
        };
    };
    payments: {
        parameters: {
            query?: {
                status?: "PENDING" | "SUCCEEDED" | "CLOSED";
                orderId?: string;
                customerId?: string;
                paymentId?: string;
                from?: string;
                to?: string;
                page?: number;
                size?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TransactionPageViewManagedPaymentView"];
                };
            };
        };
    };
    payment: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ManagedPaymentView"];
                };
            };
        };
    };
    history_1: {
        parameters: {
            query?: {
                status?: "UNPAID" | "PAID" | "ACCEPTED" | "DELIVERING" | "COMPLETED" | "CANCELLING" | "REFUNDING" | "CANCELLED";
                orderId?: string;
                customerId?: string;
                phone?: string;
                from?: string;
                to?: string;
                page?: number;
                size?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["History"];
                };
            };
        };
    };
    detail_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Detail"];
                };
            };
        };
    };
    search_1: {
        parameters: {
            query?: {
                phone?: string;
                name?: string;
                enabled?: boolean;
                from?: string;
                to?: string;
                page?: number;
                size?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CustomerPageView"];
                };
            };
        };
    };
    get_5: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ManagedCustomerView"];
                };
            };
        };
    };
    search_2: {
        parameters: {
            query?: {
                action?: "BOOTSTRAP" | "LOGIN" | "LOGOUT" | "CREATE_EMPLOYEE" | "UPDATE_EMPLOYEE" | "CHANGE_STATUS" | "CHANGE_CUSTOMER_STATUS" | "CHANGE_PASSWORD" | "AUTHORIZATION_DENIED" | "LOGIN_LIMITED";
                actorId?: string;
                subjectId?: string;
                successful?: boolean;
                from?: string;
                to?: string;
                page?: number;
                size?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AuditPageView"];
                };
            };
        };
    };
    image_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ImageLink"];
                };
            };
        };
    };
    get_6: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CartView"];
                };
            };
        };
    };
    delete_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 当前会话已撤销 */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    logout: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 当前顾客会话已撤销 */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
}
