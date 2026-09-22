import { api } from '@/shared/api/client'
import { resource } from '@/shared/api/result'
import { ApiProblem, problemFromResponse } from '@/shared/api/problem'
import type { Period } from '../model/reporting'
const xlsxType = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
/** JSON与导出都沿用统一会话客户端，浏览器不重新计算金额汇总或构造工作簿。 */
export const reportsApi = {
  operations: async (query: Period, signal?: AbortSignal) =>
    resource(await api.GET('/api/v1/reports/operations', { params: { query }, signal })),
  sales: async (query: Period, limit: number, signal?: AbortSignal) =>
    resource(
      await api.GET('/api/v1/reports/sales', { params: { query: { ...query, limit } }, signal }),
    ),
  reconciliation: async (query: Period, signal?: AbortSignal) =>
    resource(await api.GET('/api/v1/reports/reconciliation', { params: { query }, signal })),
  export: async (query: Period, signal?: AbortSignal) => {
    const result = await api.GET('/api/v1/reports/export', {
      params: { query },
      signal,
      parseAs: 'blob',
    })
    const blob = resource(result)
    return validateWorkbook(blob, result.response)
  },
}
/** 即使代理返回200 JSON也拒绝下载，避免把Problem错误或登录页保存为XLSX。 */
export async function validateWorkbook(blob: Blob, response: Response): Promise<Blob> {
  const type = response.headers.get('Content-Type')?.split(';')[0]?.trim()
  if (type?.includes('json')) {
    let body: unknown
    try {
      body = JSON.parse(await blob.text())
    } catch {
      /* 错误正文不可解析时显示安全兜底。 */
    }
    throw problemFromResponse(body, response)
  }
  if (type !== xlsxType || !blob.size)
    throw new ApiProblem(502, '导出文件格式不正确，请稍后重试', 'INVALID_EXPORT')
  return blob
}
