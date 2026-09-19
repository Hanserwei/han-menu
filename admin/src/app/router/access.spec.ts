import { describe, it, expect, vi } from 'vitest'
import { createRouter, createMemoryHistory } from 'vue-router'
import { accessGuard } from './access'

function setup(authenticated: boolean, role = 'STAFF') {
  const session = {
    restore: vi.fn().mockResolvedValue(undefined),
    authenticated,
    identity: authenticated ? { role } : null,
  }
  const view = { template: '<div />' }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', name: 'login', component: view },
      { path: '/workspace', name: 'workspace', component: view, meta: { requiresAuth: true } },
      { path: '/settings/employees', component: view, meta: { requiresAuth: true, admin: true } },
      { path: '/forbidden', name: 'forbidden', component: view, meta: { requiresAuth: true } },
    ],
  })
  router.beforeEach(accessGuard(session))
  return { router, session }
}
describe('权限路由', () => {
  it('未登录深链接先恢复认证并保存返回地址', async () => {
    const { router, session } = setup(false)
    await router.push('/settings/employees')
    expect(session.restore).toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/settings/employees')
  })
  it('普通员工不能通过手写 URL 访问管理员页面', async () => {
    const { router } = setup(true)
    await router.push('/settings/employees')
    expect(router.currentRoute.value.name).toBe('forbidden')
  })
  it('有效管理员可访问管理员路由', async () => {
    const { router } = setup(true, 'ADMIN')
    await router.push('/settings/employees')
    expect(router.currentRoute.value.path).toBe('/settings/employees')
  })
})
