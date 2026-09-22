import { createRouter, createWebHistory } from 'vue-router'
import { useSessionStore, loadLoginPage, loadAccountPage } from '@/modules/auth'
import { loadNotificationsPage } from '@/modules/notifications'
import { loadOrdersPage } from '@/modules/orders'
import { loadWorkspacePage } from '@/modules/workspace'
import { loadEmployeesPage } from '@/modules/employees'
import { loadCustomersPage } from '@/modules/customers'
import { loadShopPage } from '@/modules/shop'
import { loadCategoriesPage, loadProductsPage, loadProductEditorPage } from '@/modules/catalog'
import AppShell from '../layouts/AppShell.vue'
import StatusPage from '../layouts/StatusPage.vue'
import { loadTransactionsPage } from '@/modules/finance'
import { loadReportsPage, loadReconciliationPage } from '@/modules/reports'
import { loadAuditPage } from '@/modules/audit'
import { loadMaintenancePage } from '@/modules/maintenance'
import { accessGuard } from './access'
import { recoverPage } from './page-loader'

/** 业务路由携带权限元信息，未登录访问深链接先恢复认证；服务端仍是最终权限边界。 */
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: recoverPage(loadLoginPage),
      meta: { title: '登录' },
    },
    {
      path: '/',
      component: AppShell,
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/workspace' },
        {
          path: 'notifications',
          component: recoverPage(loadNotificationsPage),
          meta: { title: '通知中心', group: '日常作业' },
        },
        {
          path: 'orders/:id?',
          component: recoverPage(loadOrdersPage),
          meta: { title: '订单中心', group: '日常作业' },
        },
        {
          path: 'workspace',
          name: 'workspace',
          component: recoverPage(loadWorkspacePage),
          meta: { title: '工作台', group: '日常作业' },
        },
        {
          path: 'account',
          name: 'account',
          component: recoverPage(loadAccountPage),
          meta: { title: '我的账号', group: '账号' },
        },
        {
          path: 'forbidden',
          name: 'forbidden',
          component: StatusPage,
          props: { kind: 'forbidden' },
          meta: { title: '无访问权限' },
        },
        ...(import.meta.env.DEV
          ? [
              {
                path: '_dev/components',
                component: recoverPage(() => import('../testing/ComponentLab.vue')),
                meta: { title: '组件验证', admin: true },
              },
            ]
          : []),
        {
          path: 'settings/employees',
          component: recoverPage(loadEmployeesPage),
          meta: { title: '员工管理', group: '系统管理', admin: true },
        },
        {
          path: 'settings/shop',
          component: recoverPage(loadShopPage),
          meta: { title: '门店设置', group: '系统管理', admin: true },
        },
        {
          path: 'customers/:id?',
          component: recoverPage(loadCustomersPage),
          meta: { title: '顾客管理', group: '经营管理', admin: true },
        },
        {
          path: 'catalog/categories',
          component: recoverPage(loadCategoriesPage),
          meta: { title: '分类管理', group: '商品管理', admin: true },
        },
        ...(['dishes', 'meals'] as const).flatMap((segment) => {
          const kind = segment === 'dishes' ? 'DISH' : 'SET_MEAL'
          const label = kind === 'DISH' ? '菜品' : '套餐'
          return [
            {
              path: `catalog/${segment}`,
              component: recoverPage(loadProductsPage),
              props: { kind },
              meta: { title: `${label}管理`, group: '商品管理', admin: true },
            },
            {
              path: `catalog/${segment}/new`,
              component: recoverPage(loadProductEditorPage),
              props: { kind },
              meta: { title: `新增${label}`, group: '商品管理', admin: true },
            },
            {
              path: `catalog/${segment}/:id/edit`,
              component: recoverPage(loadProductEditorPage),
              props: { kind },
              meta: { title: `编辑${label}`, group: '商品管理', admin: true },
            },
          ]
        }),
        {
          path: 'finance/payments/:id?',
          component: recoverPage(loadTransactionsPage),
          meta: { title: '支付流水', group: '经营管理', admin: true },
        },
        {
          path: 'finance/refunds/:id?',
          component: recoverPage(loadTransactionsPage),
          meta: { title: '退款流水', group: '经营管理', admin: true },
        },
        {
          path: 'finance/reconciliation',
          component: recoverPage(loadReconciliationPage),
          meta: { title: '资金对账', group: '经营管理', admin: true },
        },
        {
          path: 'reports',
          component: recoverPage(loadReportsPage),
          meta: { title: '经营分析', group: '经营管理', admin: true },
        },
        {
          path: 'settings/audit',
          component: recoverPage(loadAuditPage),
          meta: { title: '安全审计', group: '系统管理', admin: true },
        },
        {
          path: 'settings/maintenance',
          component: recoverPage(loadMaintenancePage),
          meta: { title: '系统维护', group: '系统管理', admin: true },
        },
        {
          path: ':pathMatch(.*)*',
          component: StatusPage,
          props: { kind: 'missing' },
          meta: { title: '页面不存在' },
        },
      ],
    },
  ],
})
export function installRouteGuards() {
  router.beforeEach(accessGuard(useSessionStore()))
  router.afterEach((to) => {
    document.title = `${String(to.meta.title || '商家管理')} · HAN MENU`
  })
}
