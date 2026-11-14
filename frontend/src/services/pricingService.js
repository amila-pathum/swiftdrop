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
