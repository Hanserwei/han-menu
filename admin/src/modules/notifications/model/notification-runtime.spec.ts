import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest'
import { NotificationRuntime, type RuntimeState, type SocketPort } from './notification-runtime'
import { PROTOCOL, type Notice, type Receipt } from './protocol'
import { ApiProblem } from '@/shared/api/problem'

class TestSocket implements SocketPort {
  onopen: SocketPort['onopen'] = null
  onmessage: SocketPort['onmessage'] = null
  onerror: SocketPort['onerror'] = null
  onclose: SocketPort['onclose'] = null
  send = vi.fn()
  close = vi.fn()
  frame(value: unknown) {
    this.onmessage?.(new MessageEvent('message', { data: JSON.stringify(value) }))
  }
  ready() {
    this.frame({ type: 'READY', protocol: PROTOCOL })
  }
  lost() {
    this.onclose?.(new CloseEvent('close', { code: 1006 }))
  }
}
const notice = (sequence: number): Notice => ({
  id: `00000000-0000-4000-8000-${String(sequence).padStart(12, '0')}`,
  orderId: '11111111-1111-4111-8111-111111111111',
  sequence,
  type: 'NEW_ORDER',
  occurredAt: '2026-09-21T00:00:00Z',
})
async function settle() {
  for (let i = 0; i < 40; i++) await Promise.resolve()
}
let runtime: NotificationRuntime
function harness() {
  let messages: Notice[] = [],
    receipt: Receipt = { sequence: 0, version: 0 },
    state!: RuntimeState
  const sockets: TestSocket[] = []
  const api = {
    receipt: vi.fn(async () => ({ ...receipt })),
    page: vi.fn(async (after: number) => ({
      after,
      items: messages.filter((item) => item.sequence > after).slice(0, 50),
      nextCursor:
        messages
          .filter((item) => item.sequence > after)
          .slice(0, 50)
          .at(-1)?.sequence ?? after,
      hasMore: messages.filter((item) => item.sequence > after).length > 50,
    })),
    ticket: vi.fn(async () => ({
      ticket: `hmw_${'x'.repeat(43)}`,
      protocol: PROTOCOL,
      expiresAt: new Date(Date.now() + 30000).toISOString(),
    })),
    acknowledge: vi.fn(async (sequence: number, version: number) => {
      if (version !== receipt.version) throw new ApiProblem(409, '阅读进度已变化')
      receipt = { sequence, version: version + 1 }
      return receipt
    }),
  }
  const online = vi.fn(() => true),
    invalidate = vi.fn()
  runtime = new NotificationRuntime({
    api,
    socket: () => {
      const socket = new TestSocket()
      sockets.push(socket)
      return socket
    },
    changed: (value) => (state = value),
    invalidateOrders: invalidate,
    online,
    visible: () => true,
    now: () => Date.now(),
    random: () => 0,
  })
  return {
    api,
    sockets,
    online,
    invalidate,
    state: () => state,
    setMessages: (items: Notice[]) => (messages = items),
    setReceipt: (value: Receipt) => (receipt = value),
  }
}
beforeEach(() => vi.useFakeTimers())
afterEach(() => {
  runtime?.stop()
  vi.clearAllTimers()
  vi.useRealTimers()
})
describe('通知连接与阅读生命周期', () => {
  it('重复启动只建立一条连接，后台完整补查不自动确认阅读', async () => {
    const h = harness()
    h.setMessages(Array.from({ length: 125 }, (_, i) => notice(i + 1)))
    runtime.start()
    runtime.start()
    await settle()
    h.sockets[0]!.ready()
    await settle()
    expect(h.sockets).toHaveLength(1)
    expect(h.state().cursor).toBe(125)
    expect(h.state().receipt?.sequence).toBe(0)
    expect(h.api.acknowledge).not.toHaveBeenCalled()
    expect(h.state().activity).toBeNull()
    expect(h.api.page.mock.calls.map((call) => call[0])).toEqual(
      expect.arrayContaining([0, 50, 100]),
    )
  })
  it('重复或乱序实时帧只触发补查，绝不把帧序号当已读或跳过HTTP记录', async () => {
    const h = harness()
    runtime.start()
    await settle()
    h.sockets[0]!.ready()
    await settle()
    h.setMessages([notice(1), notice(2), notice(3)])
    h.sockets[0]!.frame(notice(3))
    h.sockets[0]!.frame(notice(1))
    h.sockets[0]!.frame(notice(3))
    await settle()
    expect(h.state().cursor).toBe(3)
    expect(h.state().receipt?.sequence).toBe(0)
    expect(h.state().activity?.revision).toBe(1)
    h.sockets[0]!.frame(notice(999))
    await settle()
    expect(h.state().cursor).toBe(3)
    expect(h.state().activity?.revision).toBe(1)
  })
  it('只有当前已读下界开始的实际一页可确认，不扩展到后台最新消息', async () => {
    const h = harness()
    h.setMessages(Array.from({ length: 55 }, (_, i) => notice(i + 1)))
    runtime.start()
    await settle()
    const page = await h.api.page(0)
    await runtime.acknowledge(page)
    expect(h.api.acknowledge).toHaveBeenCalledWith(50, 0, expect.any(AbortSignal))
    expect(h.state().receipt?.sequence).toBe(50)
    expect(h.state().cursor).toBe(55)
    await expect(runtime.acknowledge(page)).rejects.toMatchObject({ status: 409 })
    expect(h.api.acknowledge).toHaveBeenCalledTimes(1)
  })
  it('多设备409只重读服务端进度，不自动重新确认剩余消息', async () => {
    const h = harness()
    h.setMessages([notice(1), notice(2)])
    runtime.start()
    await settle()
    const page = await h.api.page(0)
    h.setReceipt({ sequence: 1, version: 1 })
    await expect(runtime.acknowledge(page)).rejects.toMatchObject({ status: 409 })
    expect(h.state().receipt).toEqual({ sequence: 1, version: 1 })
    expect(h.api.acknowledge).toHaveBeenCalledTimes(1)
  })
  it('确认结果已保存但响应丢失时先同步，不能再次PUT跳过未显示页', async () => {
    const h = harness()
    h.setMessages([notice(1)])
    runtime.start()
    await settle()
    h.api.acknowledge.mockImplementationOnce(async () => {
      h.setReceipt({ sequence: 1, version: 1 })
      throw new ApiProblem(0, '网络失败')
    })
    await expect(runtime.acknowledge(await h.api.page(0))).rejects.toMatchObject({ status: 0 })
    expect(h.state().receipt?.sequence).toBe(1)
    expect(h.api.acknowledge).toHaveBeenCalledTimes(1)
  })
  it('断线重连重新申请票据，旧Socket关闭事件不能启动重复连接', async () => {
    const h = harness()
    runtime.start()
    await settle()
    const old = h.sockets[0]!
    old.ready()
    await settle()
    const late = old.onclose
    old.lost()
    await settle()
    expect(old.close).toHaveBeenCalledOnce()
    await vi.advanceTimersByTimeAsync(1000)
    await settle()
    expect(h.api.ticket).toHaveBeenCalledTimes(2)
    late?.(new CloseEvent('close'))
    await settle()
    expect(h.sockets).toHaveLength(2)
  })
  it('没有PONG的连接被替换，停止后所有计时器与事件失效', async () => {
    const h = harness()
    runtime.start()
    await settle()
    h.sockets[0]!.ready()
    await settle()
    await vi.advanceTimersByTimeAsync(15000)
    expect(h.sockets[0]!.send).toHaveBeenCalledWith('ping')
    await vi.advanceTimersByTimeAsync(8000)
    expect(h.sockets[0]!.close).toHaveBeenCalledOnce()
    runtime.stop()
    const calls = h.api.ticket.mock.calls.length
    await vi.advanceTimersByTimeAsync(60000)
    expect(h.api.ticket).toHaveBeenCalledTimes(calls)
    expect(h.state().connection).toBe('stopped')
  })
  it('退出后迟到票据与补查响应不得重新创建连接或恢复提示', async () => {
    const h = harness()
    let resolve!: (value: Awaited<ReturnType<typeof h.api.ticket>>) => void
    h.api.ticket.mockReturnValueOnce(new Promise((done) => (resolve = done)))
    runtime.start()
    await settle()
    runtime.stop()
    resolve({
      ticket: 'ignored',
      protocol: PROTOCOL,
      expiresAt: new Date(Date.now() + 30000).toISOString(),
    })
    await settle()
    expect(h.sockets).toHaveLength(0)
    expect(h.state().receipt).toBeNull()
  })
  it('旧会话异步返回不能污染重新启动的新会话', async () => {
    const h = harness()
    let release!: (value: Receipt) => void
    h.api.receipt.mockReturnValueOnce(new Promise((done) => (release = done)))
    runtime.start()
    await settle()
    runtime.stop()
    h.setReceipt({ sequence: 8, version: 2 })
    runtime.start()
    await settle()
    release({ sequence: 999, version: 999 })
    await settle()
    expect(h.state().receipt).toEqual({ sequence: 8, version: 2 })
    expect(h.state().cursor).toBe(8)
  })
  it('离线恢复后能重新连接，不能遗留已清除的重连计时器标记', async () => {
    const h = harness()
    runtime.start()
    await settle()
    h.sockets[0]!.ready()
    h.sockets[0]!.lost()
    h.online.mockReturnValue(false)
    runtime.wake()
    expect(h.state().connection).toBe('offline')
    h.online.mockReturnValue(true)
    runtime.wake()
    await settle()
    expect(h.sockets).toHaveLength(2)
  })
  it('HTTP补查错误不推进游标，恢复从失败页继续且不重放成功页', async () => {
    const h = harness()
    h.setMessages(Array.from({ length: 55 }, (_, i) => notice(i + 1)))
    const original = h.api.page.getMockImplementation()!
    h.api.page.mockImplementation(async (after) => {
      if (after === 50) throw new ApiProblem(503, '暂不可用')
      return original(after)
    })
    runtime.start()
    await settle()
    expect(h.state().cursor).toBe(50)
    expect(h.state().caughtUp).toBe(false)
    expect(h.state().error).toBeInstanceOf(ApiProblem)
    h.api.page.mockImplementation(original)
    await runtime.refresh()
    expect(h.state().cursor).toBe(55)
    expect(h.state().caughtUp).toBe(true)
  })
  it('票据失败时继续HTTP补查并遵守Retry-After', async () => {
    const h = harness()
    h.api.ticket.mockRejectedValue(new ApiProblem(429, '稍后重试', 'RATE_LIMITED', undefined, 60))
    runtime.start()
    await settle()
    h.setMessages([notice(1)])
    await vi.advanceTimersByTimeAsync(16000)
    expect(h.state().cursor).toBe(1)
    expect(h.api.ticket).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(44000)
    expect(h.api.ticket).toHaveBeenCalledTimes(2)
  })
  it('协议不合法的帧不写入状态，而是关闭并恢复', async () => {
    const h = harness()
    runtime.start()
    await settle()
    h.sockets[0]!.frame({ type: 'READY', protocol: 'wrong' })
    expect(h.sockets[0]!.close).toHaveBeenCalledOnce()
    expect(h.state().cursor).toBe(0)
  })
  it('并发GET的旧阅读进度不能覆盖已成功PUT的进度', async () => {
    const h = harness()
    h.setMessages([notice(1)])
    runtime.start()
    await settle()
    let release!: (value: Receipt) => void
    h.api.receipt.mockReturnValueOnce(new Promise((done) => (release = done)))
    const sync = runtime.refresh()
    await settle()
    await runtime.acknowledge(await h.api.page(0))
    release({ sequence: 0, version: 0 })
    await sync
    expect(h.state().receipt).toEqual({ sequence: 1, version: 1 })
    expect(h.state().cursor).toBe(1)
  })
})
