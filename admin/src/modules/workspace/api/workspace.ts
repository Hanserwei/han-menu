import { api } from '@/shared/api/client'
import { problemFromResponse } from '@/shared/api/problem'

/** 工作台只使用员工摘要 API，不读取管理员报表或顾客资料。 */
export async function getWorkspace(signal?: AbortSignal) {
  const result = await api.GET('/api/v1/workspace', { signal })
  if (!result.response.ok) throw problemFromResponse(result.error, result.response)
  return result.data!
}
/** 门店名称来自公开资源，普通员工也能使用应用顶栏。 */
export async function getStorefront(signal?: AbortSignal) {
  const result = await api.GET('/api/v1/storefront', { signal })
  if (!result.response.ok) throw problemFromResponse(result.error, result.response)
  return result.data!
}
