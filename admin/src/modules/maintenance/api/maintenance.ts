import { api } from '@/shared/api/client'
import { resource, revision } from '@/shared/api/result'
import { ApiProblem } from '@/shared/api/problem'
import type { components } from '@/shared/api/schema'
export type Notice = components['schemas']['NoticeView']
export type Projection = components['schemas']['ProjectionStatus']
export function projectionVersion(value?: Projection): number {
  if (!value || !Number.isSafeInteger(value.version) || value.version! < 0)
    throw new ApiProblem(502, '投影版本缺失，请重新读取')
  return value.version!
}
/** 通知诊断使用原通知流的版本，不要求管理员输入或猜测版本号。 */
export const maintenanceApi = {
  projection: async (signal?: AbortSignal) =>
    resource(await api.GET('/api/v1/reports/projection', { signal })),
  rebuild: async (version: number) =>
    resource(await api.POST('/api/v1/reports/projection/rebuild', { body: { version } })),
  feed: async (after: number, signal?: AbortSignal, limit = 20) =>
    resource(
      await api.GET('/api/v1/notifications', { params: { query: { after, limit } }, signal }),
    ),
  attempts: async (id: string, signal?: AbortSignal) =>
    resource(
      await api.GET('/api/v1/notifications/{id}/attempts', { params: { path: { id } }, signal }),
    ),
  redeliver: async (notice: Notice) => {
    const { id, version } = revision(notice)
    if (notice.deliveryStatus !== 'EXHAUSTED')
      throw new ApiProblem(409, '只有投递耗尽的通知可以重投')
    return resource(
      await api.POST('/api/v1/notifications/{id}/redelivery', {
        params: { path: { id } },
        body: { version },
      }),
    )
  },
}
