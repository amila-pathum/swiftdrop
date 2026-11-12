import { callEndpoint } from './apiClient'
import { ENDPOINTS } from './endpoints'

// Public parcel tracking. PLANNED in the Parcel Service (see endpoints.js);
// rejects with NOT_IMPLEMENTED until it exists.

// Tracking numbers: letters, digits and dashes, 6 to 40 characters.
export const TRACKING_NUMBER_PATTERN = /^[A-Za-z0-9-]{6,40}$/

export function normalizeTrackingNumber(value) {
  return String(value ?? '').trim().toUpperCase()
}

export function trackParcel(trackingNumber, { signal } = {}) {
  return callEndpoint(ENDPOINTS.trackParcel, {
    params: { trackingNumber: normalizeTrackingNumber(trackingNumber) },
    signal,
  })
}
