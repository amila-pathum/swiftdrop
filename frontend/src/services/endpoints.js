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
    // Body: { email, password, rememberMe? } -> LoginResponse { accessToken,
    // tokenType, expiresIn, refreshToken, refreshExpiresIn, user }
    // ACTIVE and PENDING_APPROVAL accounts may sign in; SUSPENDED and DISABLED get 403.
  },
  logout: {
    method: 'POST',
    path: '/api/auth/logout',
    available: true,
    auth: true,
    // -> 204. Invalidates every access and refresh token of the signed-in user.
  },
  refresh: {
    method: 'POST',
    path: '/api/auth/refresh',
    available: true,
    // Body: { refreshToken } -> LoginResponse with a new access token and a
    // new refresh token; the old refresh token cannot be used again. 401 when
    // the refresh token is unknown, used, expired or revoked. Called only by
    // apiClient.refreshSession.
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
    // ADMIN only. Body: { name, email, password, role: "DISPATCHER" | "ADMIN" }
    // -> 201 { id, name, email, role, status, createdAt }.
  },
  listAdminUsers: {
    method: 'GET',
    path: '/api/admin/users',
    available: true,
    auth: true,
    // ADMIN only. Query: search, q, role, status, page (0-based), size, sort
    // -> Page<UserResponse> { content:[{id,name,email,phone,address,role,status,createdAt}],
    //    totalPages, totalElements, number, size }
  },
  getAdminUser: {
    method: 'GET',
    path: '/api/admin/users/:id',
    available: true,
    auth: true,
    // ADMIN only. -> UserResponse
  },
  changeUserStatus: {
    method: 'PATCH',
    path: '/api/admin/users/:id/status',
    available: true,
    auth: true,
    // ADMIN only. Body: { status: "ACTIVE"|"SUSPENDED"|"DISABLED" }
    // -> Updated UserResponse. Invalidates all existing tokens for the user.
    // Admin cannot change their own status (403).
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
    available: true,
    auth: true,
    // Courier Service. Called with the new courier's own token (APPLICANT_COURIER
    // while pending, ROLE_COURIER once active).
    // Body: { name, phone, vehicleType, vehicleRegistrationNumber, preferredArea }
    // -> 201 { id, status: "PENDING", submittedAt, ... }. 409 if already applied.
    // An administrator approves or rejects it; admins never create courier accounts.
  },
  myCourierApplication: {
    method: 'GET',
    path: '/api/couriers/me/application',
    available: true,
    auth: true,
    // Courier Service. Requires APPLICANT_COURIER or ROLE_COURIER.
    // -> { status, submittedAt, reviewedAt, reviewNote }. 404 if none submitted.
  },

  // ------------------------------------------------------------------ Parcel
  pricingQuote: {
    method: 'POST',
    path: '/api/pricing/quote',
    available: true,
    auth: true,
    // Body: { pickupCity, deliveryCity, weight, serviceType: 'STANDARD' | 'EXPRESS',
    //   size: 'SMALL' | 'MEDIUM' | 'LARGE' } (cities are zone localities)
    // -> { amount, currency: 'LKR', breakdown: { baseFee, weightFee, sizeFee, expressFee } }
  },
  trackParcel: {
    method: 'GET',
    path: '/api/parcels/track/:trackingNumber',
    available: true,
    // Public, no token. -> { trackingNumber, status, createdAt, deliveryCity,
    //   trackingHistory: [{ status, timestamp }] }. deliveryCity is a district name or null.
    // Invalid or unknown -> 404.
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
  // Zone: { id, name, code, localities: string[], active, createdAt, updatedAt }
  getZones: {
    method: 'GET',
    path: '/api/zones',
    available: true,
    auth: true,
    // Any signed-in user: active zones, by name. ADMIN may add ?includeInactive=true. -> [Zone]
  },
  getZoneById: {
    method: 'GET',
    path: '/api/zones/:id',
    available: true,
    auth: true,
    // -> Zone
  },
  createZone: {
    method: 'POST',
    path: '/api/zones',
    available: true,
    auth: true,
    // Admin only. Body: { name, code, localities: string[], active } -> 201 Zone
    // 409 when the code or a locality already belongs to another zone.
  },
  updateZone: {
    method: 'PUT',
    path: '/api/zones/:id',
    available: true,
    auth: true,
    // Admin only. Body: { name, code, localities: string[], active } -> Zone
  },
  enableZone: {
    method: 'PATCH',
    path: '/api/zones/:id/enable',
    available: true,
    auth: true,
    // Admin only. -> Zone
  },
  disableZone: {
    method: 'PATCH',
    path: '/api/zones/:id/disable',
    available: true,
    auth: true,
    // Admin only. -> Zone
  },
  getRateCard: {
    method: 'GET',
    path: '/api/pricing/rates',
    available: true,
    auth: true,
    // Admin only. The published rates with a row for every pair of active zones:
    // -> { version, publishedAt, publishedBy, sizeSurcharges: { small, medium, large },
    //      routes: [{ origin: { id, name, code }, destination, baseWeightKg, baseFee,
    //                 additionalKgFee, expressMultiplier }] } (rate fields null = no rate)
  },
  publishRates: {
    method: 'POST',
    path: '/api/pricing/rates',
    available: true,
    auth: true,
    // Admin only. Body: { basedOnVersion, sizeSurcharges, routes: [{ originZoneId,
    //   destinationZoneId, baseWeightKg, baseFee, additionalKgFee, expressMultiplier }] }
    // -> 201 rate card of the new version. 409 if another version was published since.
  },
  getRateVersions: {
    method: 'GET',
    path: '/api/pricing/rates/versions',
    available: true,
    auth: true,
    // Admin only. -> [{ version, publishedAt, publishedBy, routeCount }], newest first
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

// Courier-owned authenticated APIs; availability describes the route, not upstream health.
export const COURIER_ENDPOINTS = Object.freeze({
  courierProfile: { method: 'GET', path: '/api/couriers/me', available: true, auth: true },
  courierAvailability: { method: 'PATCH', path: '/api/couriers/me/availability', available: true, auth: true },
  courierDeliveries: { method: 'GET', path: '/api/couriers/me/deliveries', available: true, auth: true },
  courierDelivery: { method: 'GET', path: '/api/couriers/me/deliveries/:parcelId', available: true, auth: true },
  courierTracking: { method: 'GET', path: '/api/couriers/me/deliveries/:parcelId/tracking', available: true, auth: true },
  courierProofs: { method: 'GET', path: '/api/couriers/me/deliveries/:parcelId/proofs', available: true, auth: true },
  courierStatus: { method: 'POST', path: '/api/couriers/me/deliveries/:parcelId/status', available: true, auth: true },
  courierSubmission: { method: 'GET', path: '/api/couriers/me/delivery-submissions/:id', available: true, auth: true },
  // Optional photo: a signed S3 upload URL; the returned photoKey goes in the status update.
  courierPhotoUpload: { method: 'POST', path: '/api/couriers/me/deliveries/:parcelId/photo-uploads', available: true, auth: true },
})
