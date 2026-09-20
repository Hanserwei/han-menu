import { ApiProblem, problemFromResponse } from './problem'

/** 从类型化 HTTP 结果读取资源；无正文的成功命令使用 complete，避免把失败伪装为空数据。 */
export function resource<T>(result: {
  data?: T
  error?: unknown
  response: Response
}): NonNullable<T> {
  complete(result)
  if (result.data == null) throw new ApiProblem(502, '服务器返回的资源不完整', 'INVALID_RESOURCE')
  return result.data
}
export function complete(result: { error?: unknown; response: Response }): void {
  if (!result.response.ok) throw problemFromResponse(result.error, result.response)
}
/** 修改资源的前置条件来自服务器，缺失版本时拒绝提交而不是假设版本为零。 */
export function revision(value: { id?: string; version?: number }): {
  id: string
  version: number
} {
  if (!value.id || !Number.isSafeInteger(value.version) || value.version! < 0)
    throw new ApiProblem(502, '资源标识或版本缺失，请重新读取', 'INVALID_RESOURCE')
  return { id: value.id, version: value.version! }
}
