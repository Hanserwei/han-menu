/** 身份模块公开会话用例；页面通过加载函数保持独立分包。 */
export { useSessionStore } from './model/session.store'
export type { StaffIdentity } from './api/session'
export const loadLoginPage = () => import('./pages/LoginPage.vue')
export const loadAccountPage = () => import('./pages/AccountPage.vue')
