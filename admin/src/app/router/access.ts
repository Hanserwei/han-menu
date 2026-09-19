import type { NavigationGuard } from 'vue-router'

/** 守卫只依赖最小会话能力，方便用真实内存路由验证恢复、角色隔离和安全跳转。 */
interface SessionAccess {
  restore: () => Promise<void>
  authenticated: boolean
  identity: { role: string } | null
}
export function accessGuard(session: SessionAccess): NavigationGuard {
  return async (to) => {
    await session.restore()
    if (!session.authenticated && to.meta.requiresAuth)
      return { name: 'login', query: { redirect: to.fullPath } }
    if (session.authenticated && to.name === 'login') return { name: 'workspace' }
    if (to.meta.admin && session.identity?.role !== 'ADMIN') return { name: 'forbidden' }
    return true
  }
}
