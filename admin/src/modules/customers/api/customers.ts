import { api } from '@/shared/api/client'
import { resource } from '@/shared/api/result'
import type { components } from '@/shared/api/schema'
export type Customer = components['schemas']['ManagedCustomerView']
export interface CustomerFilter {
  name?: string
  phone?: string
  enabled?: boolean
  from?: string
  to?: string
}
/** 手机号等筛选只用于当前请求，不写入 URL、持久化缓存或诊断日志。 */
export const customersApi = {
  list: async (filter: CustomerFilter, page: number, size: number, signal?: AbortSignal) =>
    resource(
      await api.GET('/api/v1/management/customers', {
        params: { query: { ...filter, page, size } },
        signal,
      }),
    ),
  get: async (id: string, signal?: AbortSignal) =>
    resource(
      await api.GET('/api/v1/management/customers/{id}', { params: { path: { id } }, signal }),
    ),
  status: async (id: string, enabled: boolean, version: number) =>
    resource(
      await api.PATCH('/api/v1/management/customers/{id}/status', {
        params: { path: { id } },
        body: { enabled, version },
      }),
    ),
}
