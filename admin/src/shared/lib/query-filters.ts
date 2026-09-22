import dayjs from 'dayjs'
import './time'
import { ApiProblem } from '@/shared/api/problem'
export const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
/** UUID筛选在发送前校验，不把不完整输入当作无筛选请求。 */
export function optionalId(value: string) {
  const id = value.trim()
  if (id && !uuidPattern.test(id)) throw new ApiProblem(400, '请输入完整有效的 UUID')
  return id || undefined
}
export function queryText(value: unknown): string {
  if (value == null) return ''
  if (typeof value !== 'string') throw new ApiProblem(400, '查询参数不能重复')
  return value
}
export function validDate(value: string) {
  if (
    !/^\d{4}-\d{2}-\d{2}$/.test(value) ||
    dayjs(value).format('YYYY-MM-DD') !== value ||
    value < '1970-01-01'
  )
    throw new ApiProblem(400, '请输入1970—9999年之间的有效日期')
}
/** 流水与审计使用上海日期的左闭右开UTC时刻，报表不得复用此转换。 */
export function instantRange(fromDate: string, toDate: string) {
  if (fromDate) validDate(fromDate)
  if (toDate) validDate(toDate)
  if (fromDate && toDate && fromDate > toDate) throw new ApiProblem(400, '开始日期不能晚于结束日期')
  return {
    from: fromDate ? dayjs.tz(fromDate, 'Asia/Shanghai').toISOString() : undefined,
    // 先推进日历日期再按时区定位零点，不能假定所有历史经营日均为24小时。
    to: toDate
      ? dayjs.tz(dayjs(toDate).add(1, 'day').format('YYYY-MM-DD'), 'Asia/Shanghai').toISOString()
      : undefined,
  }
}
