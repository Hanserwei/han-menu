/** 财务模块只公开懒加载页面，支付与退款均为管理员只读视图。 */
export const loadTransactionsPage = () => import('./pages/TransactionsPage.vue')
