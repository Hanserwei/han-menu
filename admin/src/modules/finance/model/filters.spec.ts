import { describe, it, expect } from 'vitest'
import { compileFilter, emptyFilter, readFilter } from './filters'
import { instantRange } from '@/shared/lib/query-filters'
describe('财务筛选与时区', () => {
  it('单经营日变成上海零点起始的UTC左闭右开区间', () => {
    expect(instantRange('2026-09-22', '2026-09-22')).toEqual({
      from: '2026-09-21T16:00:00.000Z',
      to: '2026-09-22T16:00:00.000Z',
    })
  })
  it('历史时区偏移变化时仍以两个本地零点作为边界', () => {
    expect(instantRange('1991-09-15', '1991-09-15')).toEqual({
      from: '1991-09-14T15:00:00.000Z',
      to: '1991-09-15T16:00:00.000Z',
    })
  })
  it('允许单侧日期并拒绝回退、自动进位的无效日期', () => {
    expect(instantRange('2026-09-22', '').to).toBeUndefined()
    expect(() => instantRange('2026-02-30', '')).toThrow()
    expect(() => instantRange('2026-09-22', '2026-09-21')).toThrow()
  })
  it('退款不能沿用支付已关闭状态，非法UUID不退化为全量查询', () => {
    expect(() => compileFilter({ ...emptyFilter(), status: 'CLOSED' }, 'refunds')).toThrow()
    expect(() => compileFilter({ ...emptyFilter(), paymentId: 'partial' }, 'payments')).toThrow()
  })
  it('深链接拒绝重复查询参数和越界分页，保留支付引用', () => {
    expect(() => readFilter({ page: ['1', '2'] }, 'payments')).toThrow()
    expect(() => readFilter({ page: '10001' }, 'payments')).toThrow()
    expect(() => readFilter({ size: '999' }, 'payments')).toThrow()
    const id = '00000000-0000-4000-8000-000000000001'
    expect(readFilter({ paymentId: id, page: '1', size: '50' }, 'refunds')).toMatchObject({
      filter: { paymentId: id },
      page: 1,
      size: 50,
    })
  })
})
