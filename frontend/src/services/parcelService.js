import { callEndpoint, request } from './apiClient'
import { API_BASE_URL } from './config'
import { ENDPOINTS } from './endpoints'
import { getAccessToken } from './tokenStorage'

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

  cancelParcel: (id) =>
    request(`/api/parcels/${encodeURIComponent(id)}/cancel`, {
      method: 'PATCH',
      auth: true,
    }),

  downloadReceipt: async (id) => {
    const token = getAccessToken()
    if (token === null) {
      throw new Error('Please sign in to continue.')
    }
    const response = await fetch(
      `${API_BASE_URL}/api/parcels/${encodeURIComponent(id)}/receipt`,
      { headers: { Authorization: `Bearer ${token}` } },
    )
    if (!response.ok) {
      let message = `Receipt unavailable (${response.status}).`
      try {
        const body = await response.json()
        if (typeof body?.error === 'string' && body.error.trim() !== '') {
          message = body.error
        }
      } catch {
        // ignore parse errors — keep the default message
      }
      throw new Error(message)
    }
    const blob = await response.blob()
    const url = URL.createObjectURL(blob)
    try {
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = `receipt-${id}.pdf`
      document.body.appendChild(anchor)
      anchor.click()
      document.body.removeChild(anchor)
    } finally {
      URL.revokeObjectURL(url)
    }
  },
}

// Public parcel tracking through the API Gateway.

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

