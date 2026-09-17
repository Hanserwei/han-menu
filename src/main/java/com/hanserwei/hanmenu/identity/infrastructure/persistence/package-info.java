/**
 * 身份模块的 JPA 持久化适配器.
 *
 * <p>实体、Spring Data 接口和模型映射集中在本包；领域端口不暴露 ORM 类型。普通查询使用派生方法， 批量条件使用 Criteria，复杂读取按实际用例扩展投影或
 * Specification。禁止把实体返回给 HTTP 客户端。
 */
package com.hanserwei.hanmenu.identity.infrastructure.persistence;
