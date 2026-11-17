// Shared display helpers for the dispatcher pages.

export const EMPTY_PAGE = { content: [], page: 0, totalPages: 0, totalElements: 0 }

export const statusLabel = (status) => (status || '').replaceAll('_', ' ')

/** "Available", "Busy", "Offline" or "Inactive" for a courier from /api/dispatch/couriers. */
export function availabilityLabel(courier) {
  if (!courier.active) return 'Inactive'
  const value = courier.availability || (courier.available ? 'AVAILABLE' : 'OFFLINE')
  return value.charAt(0) + value.slice(1).toLowerCase()
}

export function formatDateTime(value) {
  return value ? new Date(value).toLocaleString() : '—'
}
