import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { parseSession, writeSession } from './session-storage'
import { sessionBridge } from '@/shared/api/session-bridge'
import { queryClient } from '@/shared/api/query-client'
import { ApiProblem } from '@/shared/api/problem'
const api = vi.hoisted(() => ({
  currentIdentity: vi.fn(),
  createSession: vi.fn(),
  revokeSession: vi.fn(),
  changePassword: vi.fn(),
}))
vi.mock('../api/session', () => api)
import { useSessionStore } from './session.store'
const employee = {
  id: 'bf636dcc-6c8b-4d8e-8b3e-f31a3ca5311d',
  username: 'staff',
  displayName: '测试员工',
  role: 'STAFF',
}
function stored() {
  return {
    accessToken: `hme_${'x'.repeat(43)}`,
    expiresAt: new Date(Date.now() + 1000).toISOString(),
  }
}
beforeEach(() => {
  setActivePinia(createPinia())
  sessionStorage.clear()
  sessionBridge.replace(null)
  vi.resetAllMocks()
  vi.useFakeTimers()
})
afterEach(() => {
  vi.clearAllTimers()
  vi.useRealTimers()
  queryClient.clear()
})
describe('会话生命周期', () => {
  it('拒绝顾客令牌、过期令牌和损坏存储', () => {
    expect(parseSession('{')).toBeNull()
    expect(
      parseSession(JSON.stringify({ ...stored(), accessToken: `hmc_${'x'.repeat(43)}` })),
    ).toBeNull()
    expect(parseSession(JSON.stringify({ ...stored(), expiresAt: '2000-01-01' }))).toBeNull()
  })
  it('从存储恢复时只信任 /me 的当前身份并合并并发读取', async () => {
    writeSession(stored())
    api.currentIdentity.mockResolvedValue(employee)
    const session = useSessionStore()
    await Promise.all([session.restore(), session.restore()])
    expect(api.currentIdentity).toHaveBeenCalledOnce()
    expect(session.identity).toEqual(employee)
    expect(session.authenticated).toBe(true)
  })
  it('服务器不可用时拒绝开放路由并保留重试恢复能力', async () => {
    writeSession(stored())
    api.currentIdentity
      .mockRejectedValueOnce(new ApiProblem(503, '暂不可用'))
      .mockResolvedValueOnce(employee)
    const session = useSessionStore()
    await session.restore()
    expect(session.authenticated).toBe(false)
    expect(session.status).toBe('unavailable')
    await session.restore(true)
    expect(session.authenticated).toBe(true)
  })
  it('当前身份失效时清除存储与个人查询缓存', async () => {
    writeSession(stored())
    queryClient.setQueryData(['private-data'], { id: 1 })
    api.currentIdentity.mockRejectedValue(new ApiProblem(401, '已失效'))
    const session = useSessionStore()
    await session.restore()
    expect(session.authenticated).toBe(false)
    expect(sessionStorage.length).toBe(0)
    expect(queryClient.getQueryData(['private-data'])).toBeUndefined()
  })
  it('退出后迟到的恢复响应不能重新登录', async () => {
    writeSession(stored())
    let resolve!: (value: typeof employee) => void
    api.currentIdentity.mockReturnValue(
      new Promise((done) => {
        resolve = done
      }),
    )
    const session = useSessionStore()
    const restore = session.restore()
    session.clear()
    resolve(employee)
    await restore
    expect(session.authenticated).toBe(false)
    expect(sessionStorage.length).toBe(0)
  })
  it('到期时即使没有网络请求也撤销本地身份', async () => {
    writeSession(stored())
    api.currentIdentity.mockResolvedValue(employee)
    const session = useSessionStore()
    await session.restore()
    await vi.advanceTimersByTimeAsync(1100)
    expect(session.authenticated).toBe(false)
  })
  it('退出服务失败仍清除本地，但继续向页面报告失败', async () => {
    writeSession(stored())
    api.currentIdentity.mockResolvedValue(employee)
    api.revokeSession.mockRejectedValue(new ApiProblem(0, '网络失败'))
    const session = useSessionStore()
    await session.restore()
    await expect(session.logout()).rejects.toThrow('网络失败')
    expect(session.authenticated).toBe(false)
    expect(sessionStorage.length).toBe(0)
  })
  it('改密成功使所有本地认证状态失效', async () => {
    writeSession(stored())
    api.currentIdentity.mockResolvedValue(employee)
    api.changePassword.mockResolvedValue(undefined)
    const session = useSessionStore()
    await session.restore()
    await session.updatePassword('old', 'new')
    expect(session.authenticated).toBe(false)
    expect(session.reason).toContain('重新登录')
  })
})
