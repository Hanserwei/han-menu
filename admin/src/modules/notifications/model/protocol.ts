import { ApiProblem } from '@/shared/api/problem'

export const PROTOCOL = 'han-menu.notifications.v1'
export const PAGE_SIZE = 50
export interface Notice {
  id: string
  sequence: number
  orderId: string
  type: 'NEW_ORDER' | 'ORDER_REMINDER'
  occurredAt: string
}
export interface FeedPage {
  after: number
  items: Notice[]
  nextCursor: number
  hasMore: boolean
}
export interface Receipt {
  sequence: number
  version: number
}
export interface Ticket {
  ticket: string
  expiresAt: string
  protocol: string
}
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const record = (value: unknown): Record<string, unknown> =>
  value !== null && typeof value === 'object' ? (value as Record<string, unknown>) : {}
export const safeSequence = (value: unknown): value is number =>
  typeof value === 'number' && Number.isSafeInteger(value) && value >= 0
function invalid(): never {
  throw new ApiProblem(502, '通知数据暂不可用，请重新同步', 'INVALID_NOTIFICATION_RESPONSE')
}
/** 只映射员工界面所需的固定事实，忽略自由文本和投递诊断字段。 */
export function parseNotice(value: unknown): Notice {
  const data = record(value)
  if (
    typeof data.id !== 'string' ||
    !uuid.test(data.id) ||
    typeof data.orderId !== 'string' ||
    !uuid.test(data.orderId) ||
    !safeSequence(data.sequence) ||
    data.sequence === 0 ||
    !['NEW_ORDER', 'ORDER_REMINDER'].includes(String(data.type)) ||
    typeof data.type !== 'string' ||
    typeof data.occurredAt !== 'string' ||
    !Number.isFinite(Date.parse(data.occurredAt))
  )
    invalid()
  return {
    id: data.id as string,
    orderId: data.orderId as string,
    sequence: data.sequence as number,
    type: data.type as Notice['type'],
    occurredAt: data.occurredAt as string,
  }
}
/** Feed 的游标必须来自完整连续的实际返回页，坏页整体拒绝，不能部分前移。 */
export function parsePage(value: unknown, after: number): FeedPage {
  const data = record(value)
  if (
    !safeSequence(after) ||
    !Array.isArray(data.items) ||
    data.items.length > PAGE_SIZE ||
    typeof data.hasMore !== 'boolean' ||
    !safeSequence(data.nextCursor)
  )
    invalid()
  const items = (data.items as unknown[]).map(parseNotice)
  if (
    items.some((item, index) => item.sequence !== after + index + 1) ||
    new Set(items.map((item) => item.id)).size !== items.length ||
    data.nextCursor !== (items.at(-1)?.sequence ?? after) ||
    (data.hasMore && !items.length)
  )
    invalid()
  return { after, items, nextCursor: data.nextCursor as number, hasMore: data.hasMore as boolean }
}
export function parseReceipt(value: unknown): Receipt {
  const data = record(value)
  if (!safeSequence(data.sequence) || !safeSequence(data.version)) invalid()
  return { sequence: data.sequence as number, version: data.version as number }
}
/** 票据仅在建立连接的局部变量中使用，不放入响应式状态、URL 或持久化存储。 */
export function parseTicket(value: unknown): Ticket {
  const data = record(value)
  if (
    data.protocol !== PROTOCOL ||
    typeof data.ticket !== 'string' ||
    !/^hmw_[A-Za-z0-9_-]{43}$/.test(data.ticket) ||
    typeof data.expiresAt !== 'string' ||
    !(Date.parse(data.expiresAt) > Date.now())
  )
    invalid()
  return { protocol: PROTOCOL, ticket: data.ticket as string, expiresAt: data.expiresAt as string }
}
export function streamUrl(origin: string): string {
  const url = new URL('/api/v1/notifications/stream', origin)
  if (!['http:', 'https:'].includes(url.protocol)) invalid()
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  return url.toString()
}
