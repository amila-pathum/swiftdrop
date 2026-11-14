import { callEndpoint } from './apiClient'
import { login, registerCourierAccount, registerMerchantAccount } from './authService'
import { ENDPOINTS } from './endpoints'
import { clearSession } from './tokenStorage'

// Merchant and courier sign-up and applications.
//
// The identity account (credentials, role) is created by the Identity Service,
// which exists. The business application belongs to the Merchant / Courier
// Service. Merchant applications use its existing registration/profile API.
// Courier application endpoints remain planned (see endpoints.js).

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
 * Sends a merchant's business details to the Merchant Service. Needs the
 * merchant's own session; no password or account data is sent here.
 */
export function submitMerchantApplication(details) {
  const {
    businessName, businessRegistrationNumber, contactName, businessEmail, businessPhone, businessAddress,
  } = details
  return callEndpoint(ENDPOINTS.merchantApplication, {
    body: {
      businessName,
      registrationNumber: businessRegistrationNumber,
      contactName,
      email: businessEmail,
      phone: businessPhone,
      address: businessAddress,
    },
  })
}

/** Sends a courier's vehicle details to the Courier Service. Needs the courier's own session. */
export function submitCourierApplication(details) {
  const { vehicleType, vehicleRegistrationNumber, preferredArea } = details
  return callEndpoint(ENDPOINTS.courierApplication, {
    body: { vehicleType, vehicleRegistrationNumber, preferredArea },
  })
}

// Submits an application as the account that was just created: signs in for
// this one call, then signs out again so the applicant signs in normally.
// A planned endpoint is not called at all (and no sign-in is made for it).
async function submitAsNewAccount(endpoint, email, password, submit) {
  try {
    if (!endpoint.available) {
      await callEndpoint(endpoint) // rejects with NOT_IMPLEMENTED
    }
    await login(email, password)
    await submit()
    return { applicationSubmitted: true, applicationError: null }
  } catch (error) {
    return { applicationSubmitted: false, applicationError: error }
  } finally {
    clearSession()
  }
}

/**
 * Merchant sign-up, in two steps owned by two services:
 * 1. Identity Service creates the account (MERCHANT, awaiting approval).
 *    A failure here rejects: nothing was created.
 * 2. Merchant Service receives the business application.
 *
 * Resolves to { user, applicationSubmitted, applicationError }. When step 2
 * fails or is not available yet, the account still exists and
 * applicationSubmitted is false: callers must say so, never report success.
 */
export async function applyAsMerchant(details) {
  const user = await registerMerchantAccount({
    name: details.contactName,
    email: details.contactEmail,
    password: details.password,
  })
  const outcome = await submitAsNewAccount(ENDPOINTS.merchantApplication,
    details.contactEmail, details.password, () => submitMerchantApplication(details))
  return { user, ...outcome }
}

/** Courier sign-up; same two steps and result as applyAsMerchant. */
export async function applyAsCourier(details) {
  const user = await registerCourierAccount({
    name: details.name,
    email: details.email,
    password: details.password,
    phone: details.phone,
    address: details.address,
  })
  const outcome = await submitAsNewAccount(ENDPOINTS.courierApplication,
    details.email, details.password, () => submitCourierApplication(details))
  return { user, ...outcome }
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
  const result = await callEndpoint(endpoint, { signal })
  if (role === 'MERCHANT' && result) {
    return { status: result.status, submittedAt: result.createdAt, reviewNote: result.rejectionReason }
  }
  return result
}
