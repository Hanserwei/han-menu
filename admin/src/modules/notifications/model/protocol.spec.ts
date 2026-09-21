import { describe, it, expect } from 'vitest'
import { parsePage, parseReceipt, parseTicket, parseNotice, streamUrl, PROTOCOL } from './protocol'
const notice = (sequence: number) => ({
  id: `00000000-0000-4000-8000-${String(sequence).padStart(12, '0')}`,
  orderId: '11111111-1111-4111-8111-111111111111',
  sequence,
  type: 'NEW_ORDER',
  occurredAt: '2026-09-21T00:00:00Z',
})
describe('通知契约边界', () => {
  it('只从完整连续页面推进到实际最后一条，空页保持原游标', () => {
    expect(
      parsePage({ items: [notice(11), notice(12)], nextCursor: 12, hasMore: true }, 10).nextCursor,
    ).toBe(12)
    expect(parsePage({ items: [], nextCursor: 12, hasMore: false }, 12).nextCursor).toBe(12)
  })
  it('拒绝跳号、乱序、重复标识、越界游标和空的后续页', () => {
    for (const page of [
      { items: [notice(2)], nextCursor: 2, hasMore: false },
      { items: [notice(2), notice(1)], nextCursor: 1, hasMore: false },
      { items: [notice(1), { ...notice(2), id: notice(1).id }], nextCursor: 2, hasMore: false },
      { items: [notice(1)], nextCursor: 100, hasMore: false },
      { items: [], nextCursor: 0, hasMore: true },
    ])
      expect(() => parsePage(page, 0)).toThrow()
  })
  it('拒绝未知事件、不安全长整数及损坏身份引用', () => {
    for (const patch of [
      { type: 'FAKE_SUCCESS' },
      { type: ['NEW_ORDER'] },
      { sequence: Number.MAX_SAFE_INTEGER + 1 },
      { orderId: 'not-uuid' },
      { occurredAt: 'invalid' },
    ])
      expect(() => parseNotice({ ...notice(1), ...patch })).toThrow()
    expect(() => parseReceipt({ sequence: -1, version: 0 })).toThrow()
  })
  it('票据限制协议、前缀和有效期，URL只使用同源路径', () => {
    const value = {
      ticket: `hmw_${'x'.repeat(43)}`,
      protocol: PROTOCOL,
      expiresAt: new Date(Date.now() + 30000).toISOString(),
    }
    expect(parseTicket(value).protocol).toBe(PROTOCOL)
    expect(() => parseTicket({ ...value, protocol: 'unknown' })).toThrow()
    expect(() => parseTicket({ ...value, expiresAt: '2000-01-01' })).toThrow()
    expect(streamUrl('https://example.test/notifications')).toBe(
      'wss://example.test/api/v1/notifications/stream',
    )
    expect(streamUrl('http://localhost:5173')).not.toMatch(/ticket|\?/)
  })
})
