import { createRouter, createWebHistory } from 'vue-router'
import { useSessionStore, loadLoginPage, loadAccountPage } from '@/modules/auth'
import { loadWorkspacePage } from '@/modules/workspace'
import AppShell from '../layouts/AppShell.vue'
import StatusPage from '../layouts/StatusPage.vue'
import { futureRoutes } from './navigation'
import { accessGuard } from './access'

/** 业务路由携带权限元信息，未登录访问深链接先恢复认证；服务端仍是最终权限边界。 */
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: loadLoginPage,
      meta: { title: '登录' },
    },
    {
      path: '/',
      component: AppShell,
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/workspace' },
        {
          path: 'workspace',
          name: 'workspace',
          component: loadWorkspacePage,
          meta: { title: '工作台', group: '日常作业' },
        },
        {
          path: 'account',
          name: 'account',
          component: loadAccountPage,
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
                component: () => import('../testing/ComponentLab.vue'),
                meta: { title: '组件验证', admin: true },
              },
            ]
          : []),
        ...futureRoutes.map((route) => ({
          path: route.path.slice(1),
          component: StatusPage,
          props: { kind: 'upcoming' },
          meta: { title: route.title, admin: route.admin },
        })),
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
