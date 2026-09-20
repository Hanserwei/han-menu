/** 订单模块对应用公开路由页面，对工作台只提供待接单部件，不暴露内部状态机。 */
export const loadOrdersPage = () => import('./pages/OrdersPage.vue')
export { default as AwaitingOrders } from './ui/AwaitingOrders.vue'
