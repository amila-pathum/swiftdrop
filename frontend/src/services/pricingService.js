import { ApiError, ApiErrorKind, callEndpoint } from './apiClient'
import { ENDPOINTS } from './endpoints'

export async function getDeliveryQuote({
  pickupCity, deliveryCity, weight, serviceType,
}, { signal } = {}) {
  const response = await callEndpoint(ENDPOINTS.pricingQuote, {
    body: {
      pickupCity: pickupCity.trim(),
      deliveryCity: deliveryCity.trim(),
      weight: Number(weight),
      serviceType,
    },
    signal,
  })

  const validBreakdown = response?.breakdown
    && ['baseFee', 'weightFee', 'expressFee']
      .every((key) => Number.isFinite(Number(response.breakdown[key])))

  if (!Number.isFinite(Number(response?.amount))
    || response.currency !== 'LKR'
    || !validBreakdown) {
    throw new ApiError('The pricing service returned an unexpected quote.', {
      kind: ApiErrorKind.INVALID_RESPONSE,
    })
  }

  return response
}

export const pricingService = {
  // Zones
  getZones: (options) =>
    callEndpoint(ENDPOINTS.getZones, options),

  getZoneById: (id, options) =>
    callEndpoint(ENDPOINTS.getZoneById, { ...options, params: { id } }),

  createZone: (data, options) =>
    callEndpoint(ENDPOINTS.createZone, { ...options, body: data }),

  updateZone: (id, data, options) =>
    callEndpoint(ENDPOINTS.updateZone, { ...options, params: { id }, body: data }),

  disableZone: (id, options) =>
    callEndpoint(ENDPOINTS.disableZone, { ...options, params: { id } }),

  // Pricing Rules
  getPricingRules: (options) =>
    callEndpoint(ENDPOINTS.getPricingRules, options),

  getPricingRuleById: (id, options) =>
    callEndpoint(ENDPOINTS.getPricingRuleById, { ...options, params: { id } }),

  createPricingRule: (data, options) =>
    callEndpoint(ENDPOINTS.createPricingRule, { ...options, body: data }),

  updatePricingRule: (id, data, options) =>
    callEndpoint(ENDPOINTS.updatePricingRule, { ...options, params: { id }, body: data }),

  disablePricingRule: (id, options) =>
    callEndpoint(ENDPOINTS.disablePricingRule, { ...options, params: { id } }),

  // Quote
  calculateQuote: (data, options) =>
    callEndpoint(ENDPOINTS.calculateQuote, { ...options, body: data }),

  getDeliveryQuote,
}

export default pricingService
