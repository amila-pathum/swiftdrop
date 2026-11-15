import { request } from './apiClient'

function queryString(params) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query.set(key, value)
    }
  })
  const value = query.toString()
  return value ? `?${value}` : ''
}

// Courier ids are courier-service profile ids (numbers).
export const dispatchService = {
  getUnassigned: (params = {}) =>
    request(`/api/dispatch/parcels/unassigned${queryString(params)}`, { auth: true }),

  getParcel: (id) =>
    request(`/api/dispatch/parcels/${encodeURIComponent(id)}`, { auth: true }),

  getCouriers: () =>
    request('/api/dispatch/couriers', { auth: true }),

  getAssignments: (params = {}) =>
    request(`/api/dispatch/assignments${queryString(params)}`, { auth: true }),

  assignCourier: (parcelId, courierId) =>
    request('/api/dispatch/assignments', {
      method: 'POST',
      body: { parcelId, courierId: Number(courierId) },
      auth: true,
    }),

  reassignCourier: (parcelId, courierId) =>
    request(`/api/dispatch/assignments/${encodeURIComponent(parcelId)}/reassign`, {
      method: 'POST',
      body: { courierId: Number(courierId) },
      auth: true,
    }),

  cancelAssignment: (parcelId) =>
    request(`/api/dispatch/assignments/${encodeURIComponent(parcelId)}/cancel`, {
      method: 'POST',
      auth: true,
    }),

  getDeliveries: (params = {}) =>
    request(`/api/dispatch/deliveries${queryString(params)}`, { auth: true }),

  getSummary: (params = {}) =>
    request(`/api/dispatch/reports/summary${queryString(params)}`, { auth: true }),
}
