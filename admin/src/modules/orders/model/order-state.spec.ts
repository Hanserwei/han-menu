import { describe, it, expect } from 'vitest'
import { availableActions, shouldPoll, timeline, statusView, statuses } from './order-state'
import { compileFilter, emptyFilter, readFilter, filterQuery } from './order-filter'
describe('订单状态与检索契约', () => {
  it('全部八种状态只开放合法商家动作', () => {
    const expected = {
      UNPAID: [],
      PAID: ['acceptance', 'rejection', 'cancellation'],
      ACCEPTED: ['delivery', 'cancellation'],
      DELIVERING: ['completion'],
      COMPLETED: [],
      CANCELLING: [],
      REFUNDING: [],
      CANCELLED: [],
    }
    for (const status of Object.keys(statuses))
      expect(availableActions(status)).toEqual(expected[status as keyof typeof expected])
    expect(availableActions('FAKE_SUCCESS')).toEqual([])
    expect(statusView('FAKE_SUCCESS').label).toBe('未知状态')
  })
  it('已取消但迟到付款正在退款仍持续核对', () => {
    expect(shouldPoll({ status: 'CANCELLED', lifecycle: { refundStatus: 'PENDING' } })).toBe(true)
    expect(shouldPoll({ status: 'CANCELLED', lifecycle: { refundStatus: 'SUCCEEDED' } })).toBe(
      false,
    )
    expect(shouldPoll({ status: 'COMPLETED' })).toBe(false)
  })
  it('进度只保留服务器时间，不推测退款时间', () => {
    expect(
      timeline({ createdAt: '2026-09-20T00:00:00Z', lifecycle: { refundStatus: 'PENDING' } }),
    ).toEqual([{ label: '已下单', time: '2026-09-20T00:00:00Z' }])
  })
  it('上海日期筛选采用左闭右开并覆盖跨月及跨年', () => {
    expect(
      compileFilter({ ...emptyFilter(), fromDate: '2026-12-31', toDate: '2026-12-31' }),
    ).toMatchObject({ from: '2026-12-30T16:00:00.000Z', to: '2026-12-31T16:00:00.000Z' })
  })
  it('非法完整编号、手机号与日期不放宽为无条件查询', () => {
    for (const patch of [
      { orderId: 'short' },
      { customerId: '-'.repeat(36) },
      { phone: '138%' },
      { fromDate: '2026-02-30' },
      { fromDate: '2026-10-01', toDate: '2026-09-01' },
      { status: 'UNKNOWN' },
    ])
      expect(() => compileFilter({ ...emptyFilter(), ...patch })).toThrow()
  })
  it('从路由恢复只接受允许参数，不将电话序列化', () => {
    const draft = { ...emptyFilter(), phone: '+13800138009', status: 'PAID' }
    expect(filterQuery(draft, 1, 20)).not.toHaveProperty('phone')
    expect(readFilter({ status: 'PAID', page: '1', size: '20', phone: 'secret' }).draft.phone).toBe(
      '',
    )
    expect(() => readFilter({ page: '10001' })).toThrow()
    expect(() => readFilter({ status: ['PAID', 'UNPAID'] })).toThrow()
  })
})
