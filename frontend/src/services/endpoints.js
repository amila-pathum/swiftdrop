/**
 * Every backend endpoint the frontend calls, in one place.
 *
 * `available: true`  – implemented in the backend and routed by the API Gateway.
 * `available: false` – PLANNED. The contract below is what the frontend expects,
 *                      but the owning service has not implemented it yet.
 *                      `callEndpoint` refuses to call it and throws an ApiError
 *                      of kind NOT_IMPLEMENTED, so the UI shows a clear
 *                      "not available yet" message instead of a fake success.
 *
 * When a team implements a planned endpoint, they flip `available` to true
 * (and adjust the contract notes if anything changed). No other frontend code
 * needs to change.
 *
 * Paths are the public paths seen through the API Gateway (VITE_API_BASE_URL).
 */
export const ENDPOINTS = Object.freeze({
  // ---------------------------------------------------------------- Identity
  register: {
    method: 'POST',
    path: '/api/auth/register',
    available: true,
    // Body: { name, email, password, phone?, address? }
    // Creates an ACTIVE CUSTOMER -> 201 user (no token). 409 if the email is taken.
  },
  registerMerchant: {
    method: 'POST',
    path: '/api/auth/register/merchant',
    available: true,
    // Body: { name, email, password, phone?, address? } (the contact person).
    // Creates only the identity account: MERCHANT, status PENDING_APPROVAL.
    // The business application is a separate call to the Merchant Service.
  },
  registerCourier: {
    method: 'POST',
    path: '/api/auth/register/courier',
    available: true,
    // Body: { name, email, password, phone?, address? }.
    // Creates only the identity account: COURIER, status PENDING_APPROVAL.
    // The courier application is a separate call to the Courier Service.
  },
  login: {
    method: 'POST',
    path: '/api/auth/login',
    available: true,
    // Body: { email, password } -> LoginResponse { accessToken, tokenType, expiresIn, user }
    // ACTIVE and PENDING_APPROVAL accounts may sign in; SUSPENDED and DISABLED get 403.
  },
  logout: {
    method: 'POST',
    path: '/api/auth/logout',
    available: true,
    auth: true,
    // -> 204. Invalidates every access token of the signed-in user.
  },
  googleExchange: {
    method: 'POST',
    path: '/api/auth/google/exchange',
    available: true,
    // Body: { code, verifier } -> LoginResponse
  },
  currentUser: {
    method: 'GET',
    path: '/api/users/me',
    available: true,
    auth: true,
    // -> { id, name, email, phone, address, role, status, createdAt }
  },
  forgotPassword: {
    method: 'POST',
    path: '/api/auth/password-reset/request',
    available: false,
    // NOT AVAILABLE YET: the endpoint exists but answers 501 until SwiftDrop
    // has email delivery. Then: body { email } -> 202 whether or not the email
    // is registered. Until then an administrator issues reset links.
  },
  confirmPasswordReset: {
    method: 'POST',
    path: '/api/auth/password-reset/confirm',
    available: true,
    // Body: { token, newPassword } -> { message }. 400 for an unknown, used or
    // expired token. The token comes from a reset link: /reset-password#token=...
  },
  createStaffUser: {
    method: 'POST',
    path: '/api/admin/users',
    available: true,
    auth: true,
    // ADMIN only. Body: { name, email, password, role: "DISPATCHER" }
    // -> 201 { id, name, email, role, status, createdAt }.
  },

  // ---------------------------------------------------------------- Merchant
  merchantApplication: {
    method: 'POST',
    path: '/api/merchants/register',
    available: true,
    auth: true,
    // Merchant Service's implemented registration contract. Uses the applicant's JWT.
    // Body: { businessName, registrationNumber, contactName, email, phone, address }
    // -> 201 MerchantResponse with status PENDING. 409 if already applied.
  },
  myMerchantApplication: {
    method: 'GET',
    path: '/api/merchants/me',
    available: true,
    auth: true,
    // -> MerchantResponse. The application service maps createdAt/rejectionReason
    // to the status page's submittedAt/reviewNote fields. 404 if none submitted.
  },

  // ----------------------------------------------------------------- Courier
  courierApplication: {
    method: 'POST',
    path: '/api/couriers/applications',
    available: false,
    auth: true,
    // PLANNED (Courier Service). Called with the new courier's own token.
    // Body: { vehicleType, vehicleRegistrationNumber, preferredArea }
    // -> 201 { id, status: "PENDING", submittedAt }. 409 if already applied.
    //
    // Permission contract: the caller has authority APPLICANT_COURIER (pending)
    // or ROLE_COURIER (active). Use:
    //   @PreAuthorize("hasAnyAuthority('APPLICANT_COURIER','ROLE_COURIER')")
    // Never read the raw JWT "role" claim to authorize this endpoint.
  },
  myCourierApplication: {
    method: 'GET',
    path: '/api/couriers/me/application',
    available: false,
    auth: true,
    // PLANNED (Courier Service). Requires APPLICANT_COURIER or ROLE_COURIER.
    // -> { status, submittedAt, reviewedAt, reviewNote }. 404 if none submitted.
  },

  // ------------------------------------------------------------------ Parcel
  pricingQuote: {
    method: 'POST',
    path: '/api/pricing/quote',
    available: true,
    auth: true,
    // Body: { pickupCity, deliveryCity, weight, serviceType: 'STANDARD' | 'EXPRESS' }
    // -> { amount, currency: 'LKR', breakdown: { baseFee, weightFee, expressFee } }
  },
  trackParcel: {
    method: 'GET',
    path: '/api/parcels/track/:trackingNumber',
    available: true,
    // Public, no token. -> { trackingNumber, status, createdAt, estimatedDeliveryDate,
    //   origin, destination, trackingHistory: [{ status, description, location, timestamp }] }.
    // Unsupported ETA/location/description fields are null; invalid or unknown -> 404.
  },

  // ------------------------------------------------------------ Notification
  notifications: {
    method: 'GET',
    path: '/api/notifications',
    available: true,
    auth: true,
    // Notification Service. -> Array of NotificationResponse { id, userId, trackingNumber, title, message, read, createdAt }
  },
  unreadNotificationsCount: {
    method: 'GET',
    path: '/api/notifications/unread-count',
    available: true,
    auth: true,
    // Notification Service. -> { count: number }
  },
  markNotificationAsRead: {
    method: 'PATCH',
    path: '/api/notifications/:id/read',
    available: true,
    auth: true,
    // Notification Service. -> Updated NotificationResponse { id, ..., read: true }
  },

  // --------------------------------------------------------- Pricing & Zones
  getZones: {
    method: 'GET',
    path: '/api/zones',
    available: true,
    auth: true,
    // -> [{ id, name, city, district, active, createdAt, updatedAt }]
  },
  getZoneById: {
    method: 'GET',
    path: '/api/zones/:id',
    available: true,
    auth: true,
    // -> { id, name, city, district, active, createdAt, updatedAt }
  },
  createZone: {
    method: 'POST',
    path: '/api/zones',
    available: true,
    auth: true,
    // Admin only. Body: { name, city, district } -> 201 Zone
  },
  updateZone: {
    method: 'PUT',
    path: '/api/zones/:id',
    available: true,
    auth: true,
    // Admin only. Body: { name, city, district } -> Zone
  },
  disableZone: {
    method: 'PATCH',
    path: '/api/zones/:id/disable',
    available: true,
    auth: true,
    // Admin only. -> Zone
  },
  getPricingRules: {
    method: 'GET',
    path: '/api/pricing/rules',
    available: true,
    auth: true,
    // -> [{ id, zoneId, zoneName, baseFee, pricePerKg, expressMultiplier, active, createdAt, updatedAt }]
  },
  getPricingRuleById: {
    method: 'GET',
    path: '/api/pricing/rules/:id',
    available: true,
    auth: true,
    // -> { id, zoneId, zoneName, baseFee, pricePerKg, expressMultiplier, active, createdAt, updatedAt }
  },
  createPricingRule: {
    method: 'POST',
    path: '/api/pricing/rules',
    available: true,
    auth: true,
    // Admin only. Body: { zoneId, baseFee, pricePerKg, expressMultiplier } -> 201 PricingRule
  },
  updatePricingRule: {
    method: 'PUT',
    path: '/api/pricing/rules/:id',
    available: true,
    auth: true,
    // Admin only. Body: { baseFee, pricePerKg, expressMultiplier } -> PricingRule
  },
  disablePricingRule: {
    method: 'PATCH',
    path: '/api/pricing/rules/:id/disable',
    available: true,
    auth: true,
    // Admin only. -> PricingRule
  },
  calculateQuote: {
    method: 'POST',
    path: '/api/pricing/quote',
    available: true,
    auth: true,
    // Body: { pickupCity, deliveryCity, weight, serviceType }
    // -> { charge, currency, breakdown }
  },
})

/** Substitutes `:name` segments with URL-encoded values. */
export function buildPath(path, params = {}) {
  return path.replace(/:([A-Za-z]+)/g, (_match, name) => {
    if (params[name] === undefined) {
      throw new TypeError(`Missing path parameter "${name}" for ${path}`)
    }
    return encodeURIComponent(String(params[name]))
  })
}
