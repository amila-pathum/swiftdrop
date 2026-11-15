import { describe, expect, it } from 'vitest'
import {
  getStatusCategory,
  formatNotificationTitle,
  formatNotificationDescription,
  formatNotificationDate,
  formatRelativeTime,
} from './notificationFormatter'

describe('notificationFormatter', () => {
  it('correctly categorizes parcel statuses', () => {
    expect(getStatusCategory('Parcel Delivered', '')).toBe('delivered')
    expect(getStatusCategory('', 'is now Out for Delivery')).toBe('out-delivery')
    expect(getStatusCategory('', 'is now In Transit')).toBe('transit')
    expect(getStatusCategory('', 'is now Picked Up')).toBe('picked-up')
    expect(getStatusCategory('Parcel Registered', '')).toBe('created')
    expect(getStatusCategory('Delivery Attempt Failed', '')).toBe('failed')
    expect(getStatusCategory('Parcel Cancelled', '')).toBe('cancelled')
    expect(getStatusCategory('Parcel Returned', '')).toBe('returned')
  })

  it('formats titles for legacy generic notifications', () => {
    expect(formatNotificationTitle({
      title: 'Parcel Status Update',
      message: 'Parcel SD-2026-9003 is now Delivered (previously In Transit)',
    })).toBe('Parcel Delivered')

    expect(formatNotificationTitle({
      title: 'Parcel Status Update',
      message: 'Parcel SD-2026-9003 is now In Transit',
    })).toBe('Parcel In Transit')

    // Preserves specific titles
    expect(formatNotificationTitle({
      title: 'Order Created',
      message: 'Your parcel was booked.',
    })).toBe('Order Created')
  })

  it('formats descriptions and removes previous status transitions', () => {
    const legacy = {
      title: 'Parcel Status Update',
      message: 'Parcel SD-2026-9003 is now Delivered (previously In Transit)',
      trackingNumber: 'SD-2026-9003',
    }
    expect(formatNotificationDescription(legacy))
      .toBe('Your parcel SD-2026-9003 has been successfully delivered.')

    const inTransit = {
      title: 'Parcel Status Update',
      message: 'Parcel SD-123 is now In Transit (previously Picked Up)',
      trackingNumber: 'SD-123',
    }
    expect(formatNotificationDescription(inTransit))
      .toBe('Your parcel SD-123 is on the way to the delivery location.')

    // Preserves already customer-friendly descriptions
    const modern = {
      title: 'Parcel Delivered',
      message: 'Your parcel SD-999 has been successfully delivered.',
      trackingNumber: 'SD-999',
    }
    expect(formatNotificationDescription(modern))
      .toBe('Your parcel SD-999 has been successfully delivered.')
  })

  it('formats timestamp into readable format', () => {
    const date = '2026-11-14T20:22:00Z'
    const formatted = formatNotificationDate(date)
    expect(formatted).toContain('2026')
    expect(formatted).toContain('·')
  })
})
