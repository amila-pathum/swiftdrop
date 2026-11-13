
import { callEndpoint } from './apiClient'
import { ENDPOINTS } from './endpoints'

export const pricingService = {
  // Zones
  getZones: () =>
    callEndpoint(ENDPOINTS.getZones),

  getZoneById: (id) =>
    callEndpoint(ENDPOINTS.getZoneById, { params: { id } }),

  createZone: (data) =>
    callEndpoint(ENDPOINTS.createZone, { body: data }),

  updateZone: (id, data) =>
    callEndpoint(ENDPOINTS.updateZone, { params: { id }, body: data }),

  disableZone: (id) =>
    callEndpoint(ENDPOINTS.disableZone, { params: { id } }),

  // Pricing Rules
  getPricingRules: () =>
    callEndpoint(ENDPOINTS.getPricingRules),

  getPricingRuleById: (id) =>
    callEndpoint(ENDPOINTS.getPricingRuleById, { params: { id } }),

  createPricingRule: (data) =>
    callEndpoint(ENDPOINTS.createPricingRule, { body: data }),

  updatePricingRule: (id, data) =>
    callEndpoint(ENDPOINTS.updatePricingRule, { params: { id }, body: data }),

  disablePricingRule: (id) =>
    callEndpoint(ENDPOINTS.disablePricingRule, { params: { id } }),

  // Quote
  calculateQuote: (data) =>
    callEndpoint(ENDPOINTS.calculateQuote, { body: data }),
}

export default pricingService
