import { describe, expect, it } from 'vitest'
import { photoError, validateDelivery } from './courierValidation'
import { activeDelivery, dateLabel } from './deliveryStatus'
const empty = { recipientName: '', failureReason: '', note: '', locationDescription: '' }
describe('courier input rules', () => {
  it('requires recipient or photo, and leaves the location optional', () => {
    expect(validateDelivery(empty, 'DELIVERED', null)).toEqual({ recipientName: expect.any(String) })
    expect(validateDelivery({ ...empty, recipientName: 'Kasun', locationDescription: 'Front desk' }, 'DELIVERED', null)).toEqual({})
    expect(validateDelivery(empty, 'PICKED_UP', null)).toEqual({})
    expect(validateDelivery(empty, 'IN_TRANSIT', null)).toEqual({})
    expect(validateDelivery({ ...empty, locationDescription: 'Sender shop' }, 'PICKED_UP', null)).toEqual({})
  })
  it('requires a reason for failure and notes for other', () => {
    expect(validateDelivery({ ...empty, failureReason: 'OTHER', locationDescription: 'Gate' }, 'FAILED', null)).toHaveProperty('note')
    expect(validateDelivery({ ...empty, failureReason: 'RECIPIENT_ABSENT', locationDescription: 'Gate' }, 'FAILED', null)).toEqual({})
  })
  it('rejects unsupported or oversized photos', () => {
    expect(photoError({ name: 'proof.gif', type: 'image/gif', size: 5 })).toBeTruthy()
    expect(photoError({ name: 'proof.png', type: 'image/png', size: 5242881 })).toBeTruthy()
    expect(photoError({ name: 'proof.jpg', type: 'image/jpeg', size: 100 })).toBeNull()
  })
  it('does not offer actions for reassigned or terminal parcels', () => {
    expect(activeDelivery({ assignment: { active: false }, parcel: { status: 'IN_TRANSIT' } })).toBe(false)
    expect(activeDelivery({ assignment: { active: true }, parcel: { status: 'DELIVERED' } })).toBe(false)
    expect(dateLabel('2026-11-14')).toBe('14 Nov 2026')
  })
})
