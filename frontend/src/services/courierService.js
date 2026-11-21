import { ApiError, ApiErrorKind, request } from './apiClient'
import { COURIER_ENDPOINTS, buildPath } from './endpoints'

const endpoint = (name, params) => buildPath(COURIER_ENDPOINTS[name].path, params)
const get = (name, params, signal) => request(endpoint(name, params), { auth: true, signal })

async function deliveryPages(filter, signal) {
  const items = []
  for (let page = 0; page < 100; page += 1) {
    const data = await request(`${endpoint('courierDeliveries')}?${new URLSearchParams({ filter, page, size: 100 })}`, { auth: true, signal })
    if (!Array.isArray(data?.content) || !Number.isInteger(data.totalPages) || data.totalPages < 0) {
      throw new ApiError('Unable to read the assigned deliveries.', { kind: ApiErrorKind.INVALID_RESPONSE })
    }
    items.push(...data.content)
    if (page + 1 >= data.totalPages) return items
  }
  throw new ApiError('The delivery list is too large to load completely. Contact your dispatcher.')
}

export const courierService = {
  getProfile: ({ signal } = {}) => get('courierProfile', undefined, signal),
  updateProfile: (body) => request(endpoint('courierProfile'), { method: 'PUT', auth: true, body }),
  updateAvailability: (availability) => request(endpoint('courierAvailability'), { method: 'PATCH', auth: true, body: { availability } }),
  getDeliveries: async (view, { signal } = {}) => {
    const results = view === 'today'
      ? await deliveryPages('today', signal)
      : (await Promise.all([deliveryPages('active', signal), deliveryPages('history', signal)])).flat()
    const unique = new Map()
    for (const delivery of results) {
      if (!delivery?.parcel?.id || !delivery?.assignment?.id) {
        throw new ApiError('An assigned delivery is missing required information.', { kind: ApiErrorKind.INVALID_RESPONSE })
      }
      unique.set(String(delivery.assignment.id), delivery)
    }
    return [...unique.values()]
  },
  getDelivery: (parcelId, { signal } = {}) => get('courierDelivery', { parcelId }, signal),
  getTracking: (parcelId, { signal } = {}) => get('courierTracking', { parcelId }, signal),
  getProofs: (parcelId, { signal } = {}) => get('courierProofs', { parcelId }, signal),
  getSubmission: (id, { signal } = {}) => get('courierSubmission', { id }, signal),
  submit: (parcelId, data, photo) => {
    let body = data
    if (photo) {
      body = new FormData()
      body.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }))
      body.append('photo', photo)
    }
    return request(endpoint('courierStatus', { parcelId }), { method: 'POST', auth: true, body })
  },
  getPhoto: (parcelId, proofId, { signal } = {}) => request(endpoint('courierPhoto', { parcelId, proofId }), { auth: true, signal, responseType: 'blob' }),
}
