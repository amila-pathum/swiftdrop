import { ApiError, ApiErrorKind, callEndpoint } from './apiClient'
import { ENDPOINTS } from './endpoints'
import { saveSession } from './tokenStorage'

// Identity Service calls through the API Gateway. Endpoint paths and their
// implementation status live in endpoints.js.

/**
 * Creates a CUSTOMER account. The backend returns the new user, not a token.
 * Public registration can never choose a role; the backend ignores one if sent.
 * @param {{name: string, email: string, password: string, phone?: string, address?: string}} details
 * @returns {Promise<{id, name, email, role, status, createdAt}>}
 */
export function register({ name, email, password, phone, address }) {
  return callEndpoint(ENDPOINTS.register, {
    body: { name, email, password, phone, address },
  })
}

// Checks a LoginResponse and stores its token. Shared by every sign-in method.
function saveLoginResponse(response, { persistent = false } = {}) {
  const valid = typeof response?.accessToken === 'string'
    && response.accessToken !== ''
    && response.tokenType?.toLowerCase?.() === 'bearer'
    && Number.isFinite(response.expiresIn)
    && response.expiresIn > 0
    && response.user !== null
    && typeof response.user === 'object'
  if (!valid) {
    throw new ApiError('The server returned an unexpected login response.', {
      status: 200,
      kind: ApiErrorKind.INVALID_RESPONSE,
    })
  }

  const expiresAt = saveSession(response.accessToken, response.expiresIn, undefined, { persistent })
  return { user: response.user, expiresAt }
}

/**
 * Signs in and stores the access token (sessionStorage, or localStorage with
 * `remember`). The token itself is not returned; the API client reads it.
 * @returns {Promise<{user: object, expiresAt: number}>}
 */
export async function login(email, password, { remember = false } = {}) {
  const response = await callEndpoint(ENDPOINTS.login, { body: { email, password } })
  return saveLoginResponse(response, { persistent: remember })
}

/**
 * Completes Google sign-in: exchanges the one-time code from the callback, with
 * the verifier this tab created, for the same response as a password login.
 * @returns {Promise<{user: object, expiresAt: number}>}
 */
export async function exchangeGoogleCode(code, verifier) {
  return saveLoginResponse(await callEndpoint(ENDPOINTS.googleExchange, {
    body: { code, verifier },
  }))
}

/**
 * Loads the signed-in user's profile using the stored bearer token.
 * @returns {Promise<{id, name, email, role, status, createdAt}>}
 */
export function getCurrentUser() {
  return callEndpoint(ENDPOINTS.currentUser)
}

/**
 * PLANNED: asks the Identity Service to email a password reset link.
 * Rejects with NOT_IMPLEMENTED until the endpoint exists.
 */
export function requestPasswordReset(email) {
  return callEndpoint(ENDPOINTS.forgotPassword, { body: { email } })
}
