import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiProblem } from '../api/problem'
import { sessionBridge } from '../api/session-bridge'
const ui = vi.hoisted(() => ({ success: vi.fn(), confirm: vi.fn() }))
vi.mock('antdv-next', () => ({
  App: { useApp: () => ({ message: { success: ui.success }, modal: { confirm: ui.confirm } }) },
}))
import { useCommand } from './use-command'
beforeEach(() => {
  vi.clearAllMocks()
  sessionBridge.replace('test-session')
})
describe('写命令生命周期', () => {
  it('同一提交尚未完成时拒绝重复操作', async () => {
    const command = useCommand()
    let resolve!: () => void
    const action = vi.fn(() => new Promise<void>((done) => (resolve = done)))
    const first = command.run(action)
    expect(command.pending.value).toBe(true)
    expect(await command.run(action)).toBe(false)
    expect(action).toHaveBeenCalledOnce()
    resolve()
    expect(await first).toBe(true)
    expect(command.pending.value).toBe(false)
    expect(ui.success).toHaveBeenCalledOnce()
  })
  it('409或结果未知必须重读，不能通过再次点击清掉并发保护', async () => {
    for (const status of [0, 409, 500, 503]) {
      const command = useCommand()
      const action = vi.fn().mockRejectedValue(new ApiProblem(status, '需重读'))
      expect(await command.run(action)).toBe(false)
      expect(command.needsReload.value).toBe(true)
      expect(await command.run(action)).toBe(false)
      expect(action).toHaveBeenCalledOnce()
    }
  })
  it('输入错误允许修正后再试，会话切换不显示上一账号成功提示', async () => {
    const command = useCommand()
    await command.run(vi.fn().mockRejectedValue(new ApiProblem(400, '输入错误')))
    expect(command.needsReload.value).toBe(false)
    expect(
      await command.run(async () => {
        sessionBridge.replace('another')
      }),
    ).toBe(false)
    expect(ui.success).not.toHaveBeenCalled()
  })
})
