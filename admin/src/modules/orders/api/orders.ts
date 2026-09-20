import { api } from '@/shared/api/client'
import { resource } from '@/shared/api/result'
import type { components } from '@/shared/api/schema'
export type OrderDetail = components['schemas']['Detail']
export type OrderSummary = components['schemas']['OrderSummary']
export type OrderStatus =
  | 'UNPAID'
  | 'PAID'
  | 'ACCEPTED'
  | 'DELIVERING'
  | 'COMPLETED'
  | 'CANCELLING'
  | 'REFUNDING'
  | 'CANCELLED'
export interface OrderFilter {
  status?: OrderStatus
  orderId?: string
  customerId?: string
  phone?: string
  from?: string
  to?: string
}
export type OrderAction = 'acceptance' | 'rejection' | 'cancellation' | 'delivery' | 'completion'
/** 订单只使用员工履约接口；写动作明确传入详情版本，绝不以客户端成功标志推进状态。 */
export const ordersApi = {
  list: async (filter: OrderFilter, page: number, size: number, signal?: AbortSignal) =>
    resource(
      await api.GET('/api/v1/management/orders', {
        params: { query: { ...filter, page, size } },
        signal,
      }),
    ),
  detail: async (id: string, signal?: AbortSignal) =>
    resource(await api.GET('/api/v1/management/orders/{id}', { params: { path: { id } }, signal })),
  act: async (id: string, action: OrderAction, version: number) => {
    const options = { params: { path: { id } }, body: { version } }
    switch (action) {
      case 'acceptance':
        return resource(await api.POST('/api/v1/management/orders/{id}/acceptance', options))
      case 'rejection':
        return resource(await api.POST('/api/v1/management/orders/{id}/rejection', options))
      case 'cancellation':
        return resource(await api.POST('/api/v1/management/orders/{id}/cancellation', options))
      case 'delivery':
        return resource(await api.POST('/api/v1/management/orders/{id}/delivery', options))
      case 'completion':
        return resource(await api.POST('/api/v1/management/orders/{id}/completion', options))
    }
  },
}
