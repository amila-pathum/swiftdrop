import { callEndpoint } from './apiClient'
import { ENDPOINTS } from './endpoints'

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
}

export default pricingService

