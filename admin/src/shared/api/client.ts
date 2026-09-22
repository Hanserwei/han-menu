import createClient from 'openapi-fetch'
import type { paths } from './schema'
import { ApiProblem } from './problem'
import { SessionBridge, sessionBridge } from './session-bridge'

/** 所有 API 只访问当前站点的 /api/v1，避免把员工凭证发送至其他来源。 */
export function authenticatedFetch(bridge: SessionBridge, transport: typeof fetch = fetch) {
  return async (request: Request): Promise<Response> => {
    const url = new URL(request.url)
    if (url.origin !== window.location.origin || !url.pathname.startsWith('/api/v1/')) {
      throw new ApiProblem(0, '拒绝向未授权的地址发送请求', 'INVALID_API_ORIGIN')
    }
    const session = bridge.snapshot()
    const publicRequest =
      url.pathname === '/api/v1/storefront' ||
      (url.pathname === '/api/v1/sessions' && request.method === 'POST')
    const controller = new AbortController()
    // 重建允许服务端完成最长180秒事务；超时后页面必须先查询结果，不能自动重发。
    const timeoutMs =
      request.method === 'POST' && url.pathname === '/api/v1/reports/projection/rebuild'
        ? 195_000
        : url.pathname === '/api/v1/reports/export'
          ? 60_000
          : 15_000
    const timeout = setTimeout(() => controller.abort('timeout'), timeoutMs)
    const headers = new Headers(request.headers)
    headers.delete('Authorization')
    if (!publicRequest && session.token) headers.set('Authorization', `Bearer ${session.token}`)
    try {
      const response = await transport(
        new Request(request, {
          headers,
          credentials: 'omit',
          cache: 'no-store',
          signal: AbortSignal.any([request.signal, session.signal, controller.signal]),
        }),
      )
      if (!bridge.isCurrent(session.generation)) throw new DOMException('会话已切换', 'AbortError')
      if (response.status === 401 && !publicRequest && session.token)
        bridge.expire(session.generation)
      return response
    } catch (error) {
      if (controller.signal.aborted) throw new ApiProblem(0, '请求超时，请重试', 'TIMEOUT')
      if (
        request.signal.aborted ||
        session.signal.aborted ||
        (error instanceof DOMException && error.name === 'AbortError')
      )
        throw new DOMException('请求已取消', 'AbortError')
      if (error instanceof ApiProblem) throw error
      throw new ApiProblem(0, '无法连接服务器，请检查网络后重试', 'NETWORK_UNAVAILABLE')
    } finally {
      clearTimeout(timeout)
    }
  }
}

export const api = createClient<paths>({
  baseUrl: window.location.origin,
  fetch: authenticatedFetch(sessionBridge),
})
