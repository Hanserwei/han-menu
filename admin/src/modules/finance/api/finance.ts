import { api } from '@/shared/api/client'
import { resource } from '@/shared/api/result'
import type { components, operations } from '@/shared/api/schema'
export type Payment = components['schemas']['ManagedPaymentView']
export type Refund = components['schemas']['ManagedRefundView']
export type PaymentFilter = NonNullable<operations['payments']['parameters']['query']>
export type RefundFilter = NonNullable<operations['refunds']['parameters']['query']>
/** 财务查询只读持久化事实，刷新不触发渠道查单或退款。 */
export const financeApi = {
  payments: async (query: PaymentFilter, signal?: AbortSignal) =>
    resource(await api.GET('/api/v1/management/payments', { params: { query }, signal })),
  refunds: async (query: RefundFilter, signal?: AbortSignal) =>
    resource(await api.GET('/api/v1/management/refunds', { params: { query }, signal })),
  payment: async (id: string, signal?: AbortSignal): Promise<Payment> =>
    resource(
      await api.GET('/api/v1/management/payments/{id}', { params: { path: { id } }, signal }),
    ),
  refund: async (id: string, signal?: AbortSignal): Promise<Refund> =>
    resource(
      await api.GET('/api/v1/management/refunds/{id}', { params: { path: { id } }, signal }),
    ),
}
