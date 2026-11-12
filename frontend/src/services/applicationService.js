import { callEndpoint } from './apiClient'
import { ENDPOINTS } from './endpoints'

// Merchant and courier applications. These endpoints are PLANNED (see
// endpoints.js); until they exist every call rejects with an ApiError of kind
// NOT_IMPLEMENTED, and the UI says so. Nothing here fakes a successful result.

export const ApplicationStatus = Object.freeze({
  PENDING: 'PENDING',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
})

export const VEHICLE_TYPES = Object.freeze([
  { value: 'BICYCLE', label: 'Bicycle' },
  { value: 'MOTORCYCLE', label: 'Motorcycle' },
  { value: 'THREE_WHEELER', label: 'Three-wheeler' },
  { value: 'CAR', label: 'Car' },
  { value: 'VAN', label: 'Van' },
  { value: 'LORRY', label: 'Lorry / truck' },
])

/**
 * Submits a merchant business application. The new MERCHANT account signs in
 * with the contact person's email and stays PENDING_APPROVAL until an admin
 * approves it.
 */
export function submitMerchantApplication(details) {
  const {
    businessName, businessRegistrationNumber, businessEmail, businessPhone,
    businessAddress, contactName, contactEmail, password,
  } = details
  return callEndpoint(ENDPOINTS.merchantApplication, {
    body: {
      businessName, businessRegistrationNumber, businessEmail, businessPhone,
      businessAddress, contactName, contactEmail, password,
    },
  })
}

/** Submits a courier application. The account stays PENDING_APPROVAL until approved. */
export function submitCourierApplication(details) {
  const {
    name, email, phone, address, vehicleType, vehicleRegistrationNumber, preferredArea, password,
  } = details
  return callEndpoint(ENDPOINTS.courierApplication, {
    body: {
      name, email, phone, address, vehicleType, vehicleRegistrationNumber, preferredArea, password,
    },
  })
}

const MY_APPLICATION = {
  MERCHANT: ENDPOINTS.myMerchantApplication,
  COURIER: ENDPOINTS.myCourierApplication,
}

/**
 * The signed-in merchant's or courier's latest application:
 * { status, submittedAt, reviewedAt, reviewNote }.
 * Resolves to null for roles that do not apply for approval.
 */
export async function getMyApplication(role, { signal } = {}) {
  const endpoint = MY_APPLICATION[role]
  if (!endpoint) return null
  return callEndpoint(endpoint, { signal })
}
