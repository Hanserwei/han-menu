import dayjs from 'dayjs'
import '@/shared/lib/time'
import { ApiProblem } from '@/shared/api/problem'
import { validDate } from '@/shared/lib/query-filters'
import type { components } from '@/shared/api/schema'
export type Period = { from: string; to: string }
export type Metadata = components['schemas']['Metadata']
/** 日期直接交给后端，包含起止经营日，不转换为UTC时间筛选。 */
export function period(from: string, to: string): Period {
  validDate(from)
  validDate(to)
  if (from > to || dayjs(to).diff(dayjs(from), 'day') > 365)
    throw new ApiProblem(400, '请选择包含首尾、不超过366天的日期区间')
  return { from, to }
}
export function recentDays(days: number): Period {
  const today = dayjs().tz('Asia/Shanghai')
  return {
    from: today.subtract(days - 1, 'day').format('YYYY-MM-DD'),
    to: today.format('YYYY-MM-DD'),
  }
}
/** 不同HTTP响应各有快照；只有代际与修订号都完整一致才能标为同一投影。 */
export function sameProjection(left?: Metadata, right?: Metadata) {
  return (
    !!left &&
    !!right &&
    Number.isSafeInteger(left.generation) &&
    Number.isSafeInteger(left.revision) &&
    left.generation === right.generation &&
    left.revision === right.revision
  )
}
