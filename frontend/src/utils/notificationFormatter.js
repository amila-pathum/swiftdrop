/**
 * Notification Formatter & Status Helpers
 * Transforms parcel status events and notifications into customer-friendly format.
 */

export function getStatusCategory(title = '', message = '', status = '') {
  const text = `${title} ${message} ${status}`.toUpperCase()
  if (text.includes('DELIVERED')) return 'delivered'
  if (text.includes('OUT_FOR_DELIVERY') || text.includes('OUT FOR DELIVERY')) return 'out-delivery'
  if (text.includes('IN_TRANSIT') || text.includes('IN TRANSIT')) return 'transit'
  if (text.includes('PICKED_UP') || text.includes('PICKED UP')) return 'picked-up'
  if (text.includes('CREATED') || text.includes('PENDING') || text.includes('REGISTERED')) return 'created'
  if (text.includes('FAILED')) return 'failed'
  if (text.includes('CANCELLED') || text.includes('CANCELED')) return 'cancelled'
  if (text.includes('RETURNED')) return 'returned'
  return 'default'
}

export function formatNotificationTitle(notification) {
  if (!notification) return 'Parcel Status Update'
  const title = notification.title || ''

  // If already a specific title (not the legacy generic one), keep it
  if (title && title !== 'Parcel Status Update') {
    return title
  }

  const category = getStatusCategory(title, notification.message || '', notification.status || '')
  switch (category) {
    case 'delivered':
      return 'Parcel Delivered'
    case 'out-delivery':
      return 'Parcel Out for Delivery'
    case 'transit':
      return 'Parcel In Transit'
    case 'picked-up':
      return 'Parcel Picked Up'
    case 'created':
      return 'Parcel Registered'
    case 'failed':
      return 'Delivery Attempt Failed'
    case 'cancelled':
      return 'Parcel Cancelled'
    case 'returned':
      return 'Parcel Returned'
    default:
      return title || 'Parcel Status Update'
  }
}

export function formatNotificationDescription(notification) {
  if (!notification || !notification.message) return ''
  const rawMessage = notification.message
  const tracking = notification.trackingNumber || 'your parcel'

  // If already customer-friendly description, return as is
  if (rawMessage.startsWith('Your parcel') || rawMessage.startsWith('Your delivery')) {
    return rawMessage
  }

  // Check if it's the legacy backend technical format:
  // "Parcel <tracking> is now <status> (previously <oldStatus>)" or "Parcel <tracking> status is now <status>"
  const isLegacyTechnical =
    /Parcel\s+[\w-]+\s+(?:status\s+)?is\s+now/i.test(rawMessage) ||
    /\(previously\s+[^)]+\)/i.test(rawMessage)

  if (!isLegacyTechnical) {
    return rawMessage
  }

  // Parse legacy technical formats
  const category = getStatusCategory(notification.title || '', rawMessage, notification.status || '')
  switch (category) {
    case 'delivered':
      return `Your parcel ${tracking} has been successfully delivered.`
    case 'out-delivery':
      return `Your parcel ${tracking} is out for delivery and will arrive soon.`
    case 'transit':
      return `Your parcel ${tracking} is on the way to the delivery location.`
    case 'picked-up':
      return `Your parcel ${tracking} has been picked up by the courier.`
    case 'created':
      return `Your parcel ${tracking} has been registered and is being processed.`
    case 'failed':
      return `Delivery attempt for parcel ${tracking} was unsuccessful.`
    case 'cancelled':
      return `Your delivery for parcel ${tracking} has been cancelled.`
    case 'returned':
      return `Your parcel ${tracking} is being returned to the sender.`
    default: {
      // Strip any "(previously ...)" technical transitions from custom/other messages
      return rawMessage.replace(/\s*\(previously\s+[^)]+\)/gi, '')
    }
  }
}

/**
 * Formats a date string into "Nov 14, 2026 · 8:22 PM"
 */
export function formatNotificationDate(dateString) {
  if (!dateString) return '—'
  const date = new Date(dateString)
  if (Number.isNaN(date.getTime())) return '—'

  const datePart = date.toLocaleDateString(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  })
  const timePart = date.toLocaleTimeString(undefined, {
    hour: 'numeric',
    minute: '2-digit',
    hour12: true,
  })

  return `${datePart} · ${timePart}`
}

/**
 * Formats relative time (e.g. "Just now", "5m ago", "2h ago")
 */
export function formatRelativeTime(dateString) {
  if (!dateString) return ''
  const date = new Date(dateString)
  if (Number.isNaN(date.getTime())) return ''
  const diffSec = Math.floor((Date.now() - date.getTime()) / 1000)

  if (diffSec < 60) return 'Just now'
  if (diffSec < 3600) return `${Math.floor(diffSec / 60)}m ago`
  if (diffSec < 86400) return `${Math.floor(diffSec / 3600)}h ago`
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}
