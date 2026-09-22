import { describe, it, expect, vi } from 'vitest'
import { period, recentDays, sameProjection } from './reporting'
describe('报表经营日期与独立快照', () => {
  it('允许366天包含首尾，拒绝第367天与反向日期', () => {
    expect(period('2024-01-01', '2024-12-31')).toEqual({ from: '2024-01-01', to: '2024-12-31' })
    expect(() => period('2024-01-01', '2025-01-01')).toThrow()
    expect(() => period('2026-09-22', '2026-09-21')).toThrow()
    expect(() => period('2026-02-29', '2026-03-01')).toThrow()
  })
  it('最近7个经营日使用上海今天，包含今天', () => {
    vi.useFakeTimers()
    try {
      vi.setSystemTime(new Date('2026-09-21T17:00:00Z'))
      expect(recentDays(7)).toEqual({ from: '2026-09-16', to: '2026-09-22' })
    } finally {
      vi.useRealTimers()
    }
  })
  it('缺少投影或不同代际修订不宣称同一快照', () => {
    expect(sameProjection({}, {})).toBe(false)
    expect(sameProjection({ generation: 1, revision: 2 }, { generation: 2, revision: 2 })).toBe(
      false,
    )
    expect(sameProjection({ generation: 1, revision: 2 }, { generation: 1, revision: 3 })).toBe(
      false,
    )
    expect(sameProjection({ generation: 1, revision: 2 }, { generation: 1, revision: 2 })).toBe(
      true,
    )
  })
})
