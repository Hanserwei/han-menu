import { ApiProblem } from '@/shared/api/problem'
import { instantRange, optionalId, queryText } from '@/shared/lib/query-filters'
import type { PaymentFilter, RefundFilter } from '../api/finance'
export type Kind = 'payments' | 'refunds'
export const emptyFilter = () => ({
  status: '',
  orderId: '',
  customerId: '',
  paymentId: '',
  fromDate: '',
  toDate: '',
})
export type Filter = ReturnType<typeof emptyFilter>
export function compileFilter(value: Filter, kind: Kind): PaymentFilter {
  const statuses =
    kind === 'payments' ? ['PENDING', 'SUCCEEDED', 'CLOSED'] : ['PENDING', 'SUCCEEDED']
  if (value.status && !statuses.includes(value.status)) throw new ApiProblem(400, '流水状态不合法')
  return {
    status: (value.status || undefined) as PaymentFilter['status'],
    orderId: optionalId(value.orderId),
    customerId: optionalId(value.customerId),
    paymentId: optionalId(value.paymentId),
    ...instantRange(value.fromDate, value.toDate),
  }
}
export function refundFilter(value: Filter): RefundFilter {
  return compileFilter(value, 'refunds') as RefundFilter
}
export function readFilter(query: Record<string, unknown>, kind: Kind) {
  const filter = emptyFilter()
  for (const key of Object.keys(filter) as (keyof Filter)[]) filter[key] = queryText(query[key])
  compileFilter(filter, kind)
  const page = Number(queryText(query.page) || 0),
    size = Number(queryText(query.size) || 20)
  if (!Number.isInteger(page) || page < 0 || page > 10000 || ![20, 50].includes(size))
    throw new ApiProblem(400, '分页参数不合法')
  return { filter, page, size }
}
export function statusLabel(status: string | undefined, kind: Kind) {
  if (status === 'SUCCEEDED') return kind === 'payments' ? '支付成功' : '退款成功'
  if (status === 'PENDING') return kind === 'payments' ? '待支付' : '退款处理中'
  return status === 'CLOSED' ? '已关闭' : '状态未知'
}
