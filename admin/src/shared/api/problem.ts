/** RFC 9457 的最小安全视图；不向界面暴露请求体、认证头或底层网络异常。 */
export class ApiProblem extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly code = 'REQUEST_FAILED',
    readonly traceId?: string,
    readonly retryAfter = 0,
  ) {
    super(message)
    this.name = 'ApiProblem'
  }
}

/** 只提取服务器契约明确允许展示的字段，未知响应不直接 stringify。 */
export function problemFromResponse(error: unknown, response: Response): ApiProblem {
  const fields = error && typeof error === 'object' ? (error as Record<string, unknown>) : {}
  const fallback: Record<number, string> = {
    400: '请检查输入内容',
    401: '登录状态已失效，请重新登录',
    403: '没有访问此资源的权限',
    404: '资源不存在或已不可访问',
    409: '数据已更新，请重新读取后确认',
    429: '操作过于频繁，请稍后重试',
    503: '服务暂不可用，请稍后重试',
  }
  const retry = response.headers.get('Retry-After')
  const seconds = retry
    ? /^\d+$/.test(retry)
      ? Number(retry)
      : Math.ceil((Date.parse(retry) - Date.now()) / 1000)
    : 0
  return new ApiProblem(
    response.status,
    typeof fields.detail === 'string'
      ? fields.detail.slice(0, 500)
      : fallback[response.status] || '请求失败，请稍后重试',
    typeof fields.code === 'string' ? fields.code : 'REQUEST_FAILED',
    typeof fields.traceId === 'string'
      ? fields.traceId
      : response.headers.get('X-Request-ID') || undefined,
    Number.isFinite(seconds) ? Math.max(0, Math.min(seconds, 86400)) : 0,
  )
}

/** 组件统一显示可预期错误，取消请求不需要产生错误弹窗。 */
export function errorMessage(error: unknown): string {
  return error instanceof ApiProblem ? error.message : '操作未完成，请稍后重试'
}
