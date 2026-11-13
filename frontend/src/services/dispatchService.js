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

export const dispatchService = {
  getUnassigned: (params = {}) =>
    request(`/api/dispatch/parcels/unassigned${queryString(params)}`, { auth: true }),

  getParcels: (params = {}) =>
    request(`/api/dispatch/parcels${queryString(params)}`, { auth: true }),

  getParcel: (id) =>
    request(`/api/dispatch/parcels/${encodeURIComponent(id)}`, { auth: true }),

  getCouriers: () =>
    request('/api/dispatch/couriers', { auth: true }),

  assignCourier: (parcelId, courierId) =>
    request(`/api/dispatch/parcels/${encodeURIComponent(parcelId)}/assignment`, {
      method: 'POST',
      body: { courierId },
      auth: true,
    }),

  getReport: (name, params = {}) =>
    request(`/api/dispatch/reports/${name}${queryString(params)}`, { auth: true }),

  exportReport: (name, params = {}) =>
    request(`/api/dispatch/reports/${name}.csv${queryString(params)}`, {
      auth: true,
      responseType: 'text',
    }),
}
