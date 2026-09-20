/** 本模块仅公开异步页面入口，业务 API 留在模块内部。 */
export const loadShopPage = () => import('./pages/ShopPage.vue')
