export const STATUS = {
  CREATED: { label: 'Created', tone: 'neutral', next: 'PICKED_UP', action: 'Mark picked up' },
  PICKED_UP: { label: 'Picked up', tone: 'info', next: 'IN_TRANSIT', action: 'Mark in transit' },
  IN_TRANSIT: { label: 'In transit', tone: 'info', next: 'OUT_FOR_DELIVERY', action: 'Mark out for delivery' },
  OUT_FOR_DELIVERY: { label: 'Out for delivery', tone: 'warning', action: 'Deliver or report failure' },
  DELIVERED: { label: 'Delivered', tone: 'success', action: 'Completed' },
  FAILED: { label: 'Failed', tone: 'error', action: 'Attempt failed' },
  CANCELLED: { label: 'Cancelled', tone: 'neutral', action: 'Cancelled' },
}
export const terminal = (status) => ['DELIVERED', 'FAILED', 'CANCELLED'].includes(status)
export const activeDelivery = ({ assignment, parcel }) => Boolean(assignment.active) && !terminal(parcel.status)
export const completedDelivery = ({ parcel }) => ['DELIVERED', 'FAILED'].includes(parcel.status)
export const firstName = (name) => name?.trim().split(/\s+/)[0] || 'Courier'
export function dateLabel(value, options = { day: 'numeric', month: 'short', year: 'numeric' }) {
  if (!value) return 'Not scheduled'
  const date = new Date(/^\d{4}-\d{2}-\d{2}$/.test(value) ? `${value}T12:00:00+05:30` : value)
  return Number.isNaN(date.getTime()) ? 'Date unavailable' : new Intl.DateTimeFormat('en-GB', { ...options, timeZone: 'Asia/Colombo' }).format(date)
}
export const address = (parcel, prefix) => parcel[`${prefix}Address`] || [parcel[`${prefix}AddressLine1`], parcel[`${prefix}AddressLine2`], parcel[`${prefix}City`]].filter(Boolean).join(', ') || 'Address unavailable'
export const deliveryKey = ({ assignment }) => String(assignment.id)
export function courierError(error, resource = 'deliveries') {
  if (error?.status === 404 && resource === 'profile') return 'Your courier profile has not been set up yet. Please contact your administrator.'
  if (error?.status === 403) return 'You do not have access to this delivery or your courier profile is inactive. Contact your dispatcher.'
  if (error?.status === 404) return 'This delivery could not be found or is no longer assigned to you.'
  return error?.message || 'Unable to load courier information. Please try again.'
}
