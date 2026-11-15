import { ApiError, ApiErrorKind, callEndpoint } from './apiClient'
import { ENDPOINTS } from './endpoints'

export const QUOTE_BREAKDOWN_KEYS = ['baseFee', 'weightFee', 'sizeFee', 'expressFee']

/** POST /api/pricing/quote, checking the response has the agreed shape. */
export async function getDeliveryQuote({
  pickupCity, deliveryCity, weight, serviceType, size,
}, { signal } = {}) {
  const response = await callEndpoint(ENDPOINTS.pricingQuote, {
    body: {
      pickupCity: pickupCity.trim(),
      deliveryCity: deliveryCity.trim(),
      weight: Number(weight),
      serviceType,
      size,
    },
    signal,
  })

  const validBreakdown = response?.breakdown
    && QUOTE_BREAKDOWN_KEYS.every((key) => Number.isFinite(Number(response.breakdown[key])))

  if (!Number.isFinite(Number(response?.amount))
    || response.currency !== 'LKR'
    || !validBreakdown) {
    throw new ApiError('The pricing service returned an unexpected quote.', {
      kind: ApiErrorKind.INVALID_RESPONSE,
    })
  }

  return response
}

const withQuery = (endpoint, query) => (query ? { ...endpoint, path: `${endpoint.path}?${query}` } : endpoint)

export const pricingService = {
  // Zones
  getZones: ({ includeInactive = false, ...options } = {}) =>
    callEndpoint(withQuery(ENDPOINTS.getZones, includeInactive && 'includeInactive=true'), options),

  getZoneById: (id, options) =>
    callEndpoint(ENDPOINTS.getZoneById, { ...options, params: { id } }),

  createZone: (data, options) =>
    callEndpoint(ENDPOINTS.createZone, { ...options, body: data }),

  updateZone: (id, data, options) =>
    callEndpoint(ENDPOINTS.updateZone, { ...options, params: { id }, body: data }),

  enableZone: (id, options) =>
    callEndpoint(ENDPOINTS.enableZone, { ...options, params: { id } }),

  disableZone: (id, options) =>
    callEndpoint(ENDPOINTS.disableZone, { ...options, params: { id } }),

  // Rates
  getRateCard: (options) =>
    callEndpoint(ENDPOINTS.getRateCard, options),

  publishRates: (data, options) =>
    callEndpoint(ENDPOINTS.publishRates, { ...options, body: data }),

  getRateVersions: (options) =>
    callEndpoint(ENDPOINTS.getRateVersions, options),

  // Quote
  getDeliveryQuote,
}

/**
 * Active zones' localities as select options grouped by zone, e.g.
 * [{ label: 'Western Metro', options: [{ value: 'Colombo', label: 'Colombo' }] }].
 */
export function localityOptions(zones) {
  return (Array.isArray(zones) ? zones : [])
    .filter((zone) => zone.active !== false && zone.localities?.length)
    .map((zone) => ({
      label: zone.name,
      options: zone.localities.map((locality) => ({ value: locality, label: locality })),
    }))
}

export default pricingService
