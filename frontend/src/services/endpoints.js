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
    // Body: { name, email, password, phone, address }
    // Creates an ACTIVE CUSTOMER and returns the user (no token).
    // NOTE: RegisterRequest currently accepts only name, email and password;
    // phone and address are sent but ignored until the Identity Service adds them.
  },
  login: {
    method: 'POST',
    path: '/api/auth/login',
    available: true,
    // Body: { email, password } -> LoginResponse { accessToken, tokenType, expiresIn, user }
    // Non-ACTIVE accounts currently receive 403.
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
    // -> { id, name, email, role, status, createdAt }
  },
  forgotPassword: {
    method: 'POST',
    path: '/api/auth/password-reset/request',
    available: false,
    // PLANNED (Identity Service). Body: { email } -> 202, always, so the
    // response does not reveal whether the email is registered.
  },

  // ---------------------------------------------------------------- Merchant
  merchantApplication: {
    method: 'POST',
    path: '/api/merchants/applications',
    available: false,
    // PLANNED (Merchant Service, public). Body:
    // { businessName, businessRegistrationNumber, businessEmail, businessPhone,
    //   businessAddress, contactName, contactEmail, password }
    // Creates a MERCHANT account in PENDING_APPROVAL status (login email =
    // contactEmail) -> 201 { id, status: "PENDING", submittedAt }. 409 if the
    // contact email or registration number is already used.
  },
  myMerchantApplication: {
    method: 'GET',
    path: '/api/merchants/me/application',
    available: false,
    auth: true,
    // PLANNED (Merchant Service, role MERCHANT, any account status).
    // -> { status: "PENDING" | "APPROVED" | "REJECTED", submittedAt, reviewedAt, reviewNote }
  },

  // ----------------------------------------------------------------- Courier
  courierApplication: {
    method: 'POST',
    path: '/api/couriers/applications',
    available: false,
    // PLANNED (Courier Service, public). Body:
    // { name, email, phone, address, vehicleType, vehicleRegistrationNumber,
    //   preferredArea, password }
    // Creates a COURIER account in PENDING_APPROVAL status -> 201
    // { id, status: "PENDING", submittedAt }. 409 if the email is already used.
  },
  myCourierApplication: {
    method: 'GET',
    path: '/api/couriers/me/application',
    available: false,
    auth: true,
    // PLANNED (Courier Service, role COURIER, any account status). Same shape
    // as myMerchantApplication.
  },

  // ------------------------------------------------------------------ Parcel
  trackParcel: {
    method: 'GET',
    path: '/api/parcels/track/:trackingNumber',
    available: false,
    // PLANNED (Parcel Service, public). -> { trackingNumber, status,
    //   lastUpdatedAt, history: [{ status, occurredAt, note }] }; 404 if unknown.
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
