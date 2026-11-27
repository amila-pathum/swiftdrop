import { describe, expect, it } from 'vitest'
import { finishedAt, prioritise, schedule, summarise } from './dashboard'

const d = (id, status, deliveryDate, extra = {}) => ({ assignment: { id, active: true, deliveryDate }, parcel: { id, status, ...extra } })

describe('courier dashboard rules', () => {
  it('classifies schedules against the Sri Lanka day', () => {
    expect(schedule(d('a', 'CREATED', '2026-11-27'), '2026-11-28')).toBe('overdue')
    expect(schedule(d('a', 'CREATED', '2026-11-28'), '2026-11-28')).toBe('today')
    expect(schedule(d('a', 'CREATED', '2026-12-01'), '2026-11-28')).toBe('upcoming')
    expect(schedule(d('a', 'CREATED', null), '2026-11-28')).toBe('unscheduled')
  })
  it('orders overdue, then today, then unscheduled, then upcoming, furthest stage first', () => {
    const list = [d('up', 'CREATED', '2026-12-01'), d('none', 'CREATED', null), d('todayA', 'CREATED', '2026-11-28'),
      d('todayB', 'OUT_FOR_DELIVERY', '2026-11-28'), d('late', 'PICKED_UP', '2026-11-20'), d('done', 'DELIVERED', '2026-11-01'),
      { ...d('gone', 'IN_TRANSIT', '2026-11-28'), assignment: { id: 'gone', active: false } }]
    expect(prioritise(list, '2026-11-28').map(({ parcel }) => parcel.id)).toEqual(['late', 'todayB', 'todayA', 'none', 'up'])
  })
  it('counts today from the final history entry and leaves success rate empty with no finished parcels', () => {
    const delivered = d('x', 'DELIVERED', '2026-11-20', { history: [{ status: 'OUT_FOR_DELIVERY', recordedAt: '2026-11-28T01:00:00Z' }, { status: 'DELIVERED', recordedAt: '2026-11-28T03:00:00Z' }] })
    expect(finishedAt(delivered)).toBe('2026-11-28T03:00:00Z')
    expect(summarise([delivered, d('y', 'FAILED', '2026-11-27')], '2026-11-28')).toMatchObject({ deliveredToday: 1, failedToday: 0, successRate: 50, active: 0 })
    expect(summarise([d('z', 'IN_TRANSIT', '2026-11-28')], '2026-11-28')).toMatchObject({ active: 1, dueToday: 1, successRate: null, stages: { IN_TRANSIT: 1 } })
  })
})
