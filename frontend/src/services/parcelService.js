import { request } from './apiClient'

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
