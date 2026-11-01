import { ApiError, ApiErrorKind, apiClient } from './apiClient'
import { saveSession } from './tokenStorage'

// Endpoints exposed by the Identity Service through the API Gateway.
const REGISTER_PATH = '/api/auth/register'
const LOGIN_PATH = '/api/auth/login'
const GOOGLE_EXCHANGE_PATH = '/api/auth/google/exchange'
const CURRENT_USER_PATH = '/api/users/me'

/**
 * Creates a CUSTOMER account. The backend returns the new user, not a token.
 * @returns {Promise<{id, name, email, role, status, createdAt}>}
 */
export function register(name, email, password) {
  return apiClient.post(REGISTER_PATH, { name, email, password })
}

// Checks a LoginResponse and stores its token. Shared by every sign-in method.
function saveLoginResponse(response) {
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

  const expiresAt = saveSession(response.accessToken, response.expiresIn)
  return { user: response.user, expiresAt }
}

/**
 * Signs in and stores the access token in sessionStorage.
 * The token itself is not returned; it is read from storage by the API client.
 * @returns {Promise<{user: object, expiresAt: number}>}
 */
export async function login(email, password) {
  return saveLoginResponse(await apiClient.post(LOGIN_PATH, { email, password }))
}

/**
 * Completes Google sign-in: exchanges the one-time code from the callback, with
 * the verifier this tab created, for the same response as a password login.
 * @returns {Promise<{user: object, expiresAt: number}>}
 */
export async function exchangeGoogleCode(code, verifier) {
  return saveLoginResponse(await apiClient.post(GOOGLE_EXCHANGE_PATH, { code, verifier }))
}

/**
 * Loads the signed-in user's profile using the stored bearer token.
 * @returns {Promise<{id, name, email, role, status, createdAt}>}
 */
export function getCurrentUser() {
  return apiClient.get(CURRENT_USER_PATH, { auth: true })
}
