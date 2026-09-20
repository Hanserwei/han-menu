import dayjs from 'dayjs'
import '@/shared/lib/time'
import { ApiProblem } from '@/shared/api/problem'
import type { OrderFilter, OrderStatus } from '../api/orders'
import { statuses } from './order-state'
export interface FilterDraft {
  status: string
  orderId: string
  customerId: string
  phone: string
  fromDate: string
  toDate: string
}
export const emptyFilter = (): FilterDraft => ({
  status: '',
  orderId: '',
  customerId: '',
  phone: '',
  fromDate: '',
  toDate: '',
})
export const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
/** 输入时区固定上海；结束日期转次日零点，避免与报表包含首尾日期协议混淆。 */
export function compileFilter(draft: FilterDraft): OrderFilter {
  const value = {
    ...draft,
    orderId: draft.orderId.trim(),
    customerId: draft.customerId.trim(),
    phone: draft.phone.trim(),
  }
  if (value.status && !Object.hasOwn(statuses, value.status))
    throw new ApiProblem(400, '订单状态不合法')
  if ([value.orderId, value.customerId].some((id) => id && !uuidPattern.test(id)))
    throw new ApiProblem(400, '请输入完整有效的订单或顾客 UUID')
  if (value.phone && !/^\+?[1-9][0-9]{6,14}$/.test(value.phone))
    throw new ApiProblem(400, '请输入完整收货手机号')
  for (const date of [value.fromDate, value.toDate])
    if (date && (!/^\d{4}-\d{2}-\d{2}$/.test(date) || dayjs(date).format('YYYY-MM-DD') !== date))
      throw new ApiProblem(400, '创建日期不合法')
  if (value.fromDate && value.toDate && value.fromDate > value.toDate)
    throw new ApiProblem(400, '开始日期不能晚于结束日期')
  return {
    status: (value.status || undefined) as OrderStatus | undefined,
    orderId: value.orderId || undefined,
    customerId: value.customerId || undefined,
    phone: value.phone || undefined,
    from: value.fromDate ? dayjs.tz(value.fromDate, 'Asia/Shanghai').toISOString() : undefined,
    to: value.toDate
      ? dayjs
          .tz(dayjs(value.toDate).add(1, 'day').format('YYYY-MM-DD'), 'Asia/Shanghai')
          .toISOString()
      : undefined,
  }
}
/** 只序列化非个人资料条件；手机号不会写入 URL、历史记录或查询键。 */
export function filterQuery(draft: FilterDraft, page: number, size: number) {
  return {
    status: draft.status || undefined,
    orderId: draft.orderId || undefined,
    customerId: draft.customerId || undefined,
    fromDate: draft.fromDate || undefined,
    toDate: draft.toDate || undefined,
    page: String(page),
    size: String(size),
  }
}
export function readFilter(query: Record<string, unknown>) {
  const draft = emptyFilter()
  for (const key of ['status', 'orderId', 'customerId', 'fromDate', 'toDate'] as const) {
    const value = query[key]
    if (value != null && typeof value !== 'string') throw new ApiProblem(400, '查询参数不能重复')
    draft[key] = typeof value === 'string' ? value : ''
  }
  compileFilter(draft)
  const page = Number(query.page ?? 0),
    size = Number(query.size ?? 20)
  if (!Number.isInteger(page) || page < 0 || page > 10000 || ![20, 50].includes(size))
    throw new ApiProblem(400, '分页参数不合法')
  return { draft, page, size }
}
