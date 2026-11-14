import { callEndpoint, request } from './apiClient'
import { ENDPOINTS } from './endpoints'

export const parcelService = {
  createParcel: (parcelData) =>
    request('/api/parcels', {
      method: 'POST',
      body: parcelData,
      auth: true,
    }),

  getParcelById: (id) =>
    request(`/api/parcels/${encodeURIComponent(id)}`, {
      auth: true,
    }),

  getMyParcels: () =>
    request('/api/parcels/my', {
      auth: true,
    }),

  getParcelHistory: (id) =>
    request(`/api/parcels/${encodeURIComponent(id)}/history`, {
      auth: true,
    }),

  updateParcelStatus: (id, statusData) =>
    request(`/api/parcels/${encodeURIComponent(id)}/status`, {
      method: 'PATCH',
      body: statusData,
      auth: true,
    }),
}

// Public parcel tracking through the API Gateway; no authentication required.

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

