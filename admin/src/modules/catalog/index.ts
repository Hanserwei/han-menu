/** 本模块仅公开异步页面入口，业务 API 留在模块内部。 */
export const loadCategoriesPage = () => import('./pages/CategoriesPage.vue')
export const loadProductsPage = () => import('./pages/ProductsPage.vue')
export const loadProductEditorPage = () => import('./pages/ProductEditorPage.vue')
