/** 应用层负责绑定登录生命周期，业务页面只读取同一个通知实例。 */
export { useNotificationsStore } from './model/notifications.store'
export { default as NotificationBell } from './ui/NotificationBell.vue'
export const loadNotificationsPage = () => import('./pages/NotificationsPage.vue')
