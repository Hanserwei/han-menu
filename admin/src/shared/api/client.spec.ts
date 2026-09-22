import { describe, it, expect, vi } from 'vitest'
import { authenticatedFetch } from './client'
import { SessionBridge } from './session-bridge'
import { problemFromResponse } from './problem'

const url = 'http://localhost/api/v1/me'
describe('员工 HTTP 边界', () => {
  it('只给受保护资源附加当前 Bearer，禁用 Cookie 与缓存', async () => {
    const bridge = new SessionBridge()
    bridge.replace('private-token')
    const transport = vi.fn<typeof fetch>().mockResolvedValue(new Response('{}'))
    await authenticatedFetch(bridge, transport)(new Request(url))
    const request = transport.mock.calls[0]![0] as Request
    expect(request.headers.get('Authorization')).toBe('Bearer private-token')
    expect(request.credentials).toBe('omit')
    expect(request.cache).toBe('no-store')
  })
  it('公开登录不会携带旧凭证，也不会因登录失败撤销其他会话', async () => {
    const bridge = new SessionBridge()
    bridge.replace('old-token')
    const expired = vi.fn()
    bridge.onExpired(expired)
    const transport = vi.fn<typeof fetch>().mockResolvedValue(new Response('{}', { status: 401 }))
    await authenticatedFetch(
      bridge,
      transport,
    )(new Request('http://localhost/api/v1/sessions', { method: 'POST' }))
    expect((transport.mock.calls[0]![0] as Request).headers.has('Authorization')).toBe(false)
    expect(expired).not.toHaveBeenCalled()
  })
  it('跨来源 URL 在发送前被拒绝', async () => {
    const transport = vi.fn<typeof fetch>()
    await expect(
      authenticatedFetch(
        new SessionBridge(),
        transport,
      )(new Request('https://example.com/api/v1/me')),
    ).rejects.toMatchObject({ code: 'INVALID_API_ORIGIN' })
    expect(transport).not.toHaveBeenCalled()
  })
  it('当前会话收到401撤销认证，403不误清除有效会话', async () => {
    const bridge = new SessionBridge()
    bridge.replace('token')
    const expired = vi.fn()
    bridge.onExpired(expired)
    await authenticatedFetch(
      bridge,
      vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 403 })),
    )(new Request(url))
    expect(expired).not.toHaveBeenCalled()
    await authenticatedFetch(
      bridge,
      vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 401 })),
    )(new Request(url))
    expect(expired).toHaveBeenCalledOnce()
  })
  it('上个账号的迟到401不能撤销新账号', async () => {
    const bridge = new SessionBridge()
    bridge.replace('old')
    const expired = vi.fn()
    bridge.onExpired(expired)
    let resolve!: (value: Response) => void
    const transport = vi.fn<typeof fetch>().mockImplementation(
      () =>
        new Promise<Response>((done) => {
          resolve = done
        }),
    )
    const promise = authenticatedFetch(bridge, transport)(new Request(url))
    bridge.replace('new')
    resolve(new Response(null, { status: 401 }))
    await expect(promise).rejects.toMatchObject({ name: 'AbortError' })
    expect(expired).not.toHaveBeenCalled()
  })
  it('切换会话主动取消旧请求，即使200迟到也不交付旧数据', async () => {
    const bridge = new SessionBridge()
    bridge.replace('old')
    let resolve!: (value: Response) => void
    const transport = vi.fn<typeof fetch>().mockImplementation(
      () =>
        new Promise<Response>((done) => {
          resolve = done
        }),
    )
    const promise = authenticatedFetch(bridge, transport)(new Request(url))
    const request = transport.mock.calls[0]![0] as Request
    bridge.replace(null)
    expect(request.signal.aborted).toBe(true)
    resolve(new Response('{}'))
    await expect(promise).rejects.toMatchObject({ name: 'AbortError' })
  })
  it('规范错误保留追踪号和限流时限，不回显未知服务器正文', () => {
    const problem = problemFromResponse(
      { detail: '操作频繁', code: 'RATE_LIMITED', traceId: 'test-trace' },
      new Response(null, { status: 429, headers: { 'Retry-After': '12' } }),
    )
    expect(problem).toMatchObject({
      status: 429,
      message: '操作频繁',
      retryAfter: 12,
      traceId: 'test-trace',
    })
    expect(
      problemFromResponse('<html>secret</html>', new Response(null, { status: 500 })).message,
    ).not.toContain('secret')
  })
})

describe('维护与下载超时边界', () => {
  it.each([
    ['/api/v1/reports/projection/rebuild', 'POST', 195_000],
    ['/api/v1/reports/export?from=2026-09-01&to=2026-09-22', 'GET', 60_000],
    ['/api/v1/reports/projection', 'GET', 15_000],
  ])('只给 %s 配置相应等待上限', async (path, method, timeout) => {
    vi.useFakeTimers()
    try {
      const bridge = new SessionBridge()
      bridge.replace('test-session')
      const transport = vi.fn<typeof fetch>().mockImplementation(
        (input) =>
          new Promise((_resolve, reject) => {
            const request = input as Request
            request.signal.addEventListener(
              'abort',
              () => reject(new DOMException('aborted', 'AbortError')),
              { once: true },
            )
          }),
      )
      const promise = authenticatedFetch(
        bridge,
        transport,
      )(new Request('http://localhost' + path, { method }))
      const rejected = expect(promise).rejects.toMatchObject({ code: 'TIMEOUT' })
      await vi.advanceTimersByTimeAsync(timeout - 1)
      expect((transport.mock.calls[0]![0] as Request).signal.aborted).toBe(false)
      await vi.advanceTimersByTimeAsync(1)
      await rejected
    } finally {
      vi.useRealTimers()
    }
  })
})
