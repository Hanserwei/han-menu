/** 工作台公开门店读取和异步页面入口，避免布局提前装载业务页面。 */
export { getStorefront } from './api/workspace'
export const loadWorkspacePage = () => import('./pages/WorkspacePage.vue')
