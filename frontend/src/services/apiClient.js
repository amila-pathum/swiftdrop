import { API_BASE_URL } from './config'
import { ENDPOINTS, buildPath } from './endpoints'
import {
  canRefresh,
  getAccessToken,
  getRefreshToken,
  getSession,
  isPersistentSession,
  saveSession,
  sessionGeneration,
} from './tokenStorage'

export const ApiErrorKind = Object.freeze({
  NETWORK: 'NETWORK',
  SERVICE_UNAVAILABLE: 'SERVICE_UNAVAILABLE',
  UNAUTHENTICATED: 'UNAUTHENTICATED',
  HTTP: 'HTTP',
  INVALID_RESPONSE: 'INVALID_RESPONSE',
  // The endpoint is planned but not implemented by the backend yet (see endpoints.js).
  NOT_IMPLEMENTED: 'NOT_IMPLEMENTED',
})

const UNAVAILABLE_STATUSES = new Set([502, 503, 504])

const MESSAGES = {
  network: 'Unable to reach the server. Check your connection and try again.',
  unavailable: 'The service is temporarily unavailable. Please try again shortly.',
  noSession: 'Please sign in to continue.',
  invalidResponse: 'The server returned an unexpected response.',
  notImplemented: 'This feature is not available yet. Please check back soon.',
}

// Messages are safe to show to users; tokens and request bodies are never included.
export class ApiError extends Error {
  constructor(message, { status = 0, kind = ApiErrorKind.HTTP } = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.kind = kind
  }
}

// Listeners told when the session can no longer be used: an authenticated
// request was rejected with 401 and the token could not be refreshed, or the
// refresh itself was refused. AuthContext ends the session.
const unauthorizedListeners = new Set()

/**
 * Registers a callback for 401 responses to authenticated requests.
 * @returns {() => void} Unsubscribe function.
 */
export function onUnauthorized(listener) {
  unauthorizedListeners.add(listener)
  return () => unauthorizedListeners.delete(listener)
}

async function readJson(response) {
  const text = await response.text()
  if (text === '') {
    return null
  }
  try {
    return JSON.parse(text)
  } catch {
    return undefined
  }
}

function errorFromResponse(response, body) {
  if (UNAVAILABLE_STATUSES.has(response.status)) {
    return new ApiError(MESSAGES.unavailable, {
      status: response.status,
      kind: ApiErrorKind.SERVICE_UNAVAILABLE,
    })
  }
  const message = typeof body?.error === 'string' && body.error.trim() !== ''
    ? body.error
    : `Request failed with status ${response.status}.`
  return new ApiError(message, { status: response.status, kind: ApiErrorKind.HTTP })
}

const notifyUnauthorized = () => unauthorizedListeners.forEach((listener) => listener())

// Statuses with which the Identity Service refuses a refresh token for good.
const REFRESH_REFUSED_STATUSES = new Set([400, 401, 403])

let refreshInFlight = null

/**
 * Exchanges the stored refresh token for new tokens and stores them in the same
 * place (sessionStorage, or localStorage for "Remember me"). Concurrent callers
 * share one request, so a refresh token is never sent twice by this tab.
 *
 * If the server refuses the refresh token, the unauthorized listeners are told
 * (AuthContext ends the session) and the promise rejects with status 401.
 * Network and server errors reject without ending the session.
 * @returns {Promise<{accessToken: string, expiresAt: number, user: object|undefined}>}
 */
export function refreshSession() {
  if (refreshInFlight === null) {
    refreshInFlight = performRefresh().finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

async function performRefresh() {
  const refreshToken = getRefreshToken()
  if (refreshToken === null) {
    throw new ApiError(MESSAGES.noSession, { status: 401, kind: ApiErrorKind.UNAUTHENTICATED })
  }
  const generation = sessionGeneration()
  const persistent = isPersistentSession()

  let response
  try {
    response = await fetch(`${API_BASE_URL}${ENDPOINTS.refresh.path}`, {
      method: ENDPOINTS.refresh.method,
      headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
  } catch {
    throw new ApiError(MESSAGES.network, { kind: ApiErrorKind.NETWORK })
  }
  const data = await readJson(response)

  if (!response.ok) {
    // Another tab sharing this remembered session may have used the token
    // first; its replacement is already in storage.
    const session = getSession()
    if (session?.refreshToken && session.refreshToken !== refreshToken
        && getAccessToken() !== null) {
      return { accessToken: session.accessToken, expiresAt: session.expiresAt, user: undefined }
    }
    if (REFRESH_REFUSED_STATUSES.has(response.status)) {
      if (generation === sessionGeneration()) {
        notifyUnauthorized()
      }
      throw new ApiError(MESSAGES.noSession, { status: 401, kind: ApiErrorKind.UNAUTHENTICATED })
    }
    throw errorFromResponse(response, data)
  }

  const valid = typeof data?.accessToken === 'string' && data.accessToken !== ''
    && Number.isFinite(data.expiresIn) && data.expiresIn > 0
    && typeof data.refreshToken === 'string' && data.refreshToken !== ''
    && Number.isFinite(data.refreshExpiresIn) && data.refreshExpiresIn > 0
  if (!valid) {
    throw new ApiError(MESSAGES.invalidResponse, {
      status: response.status,
      kind: ApiErrorKind.INVALID_RESPONSE,
    })
  }

  // Signed out or signed in again meanwhile: keep the newer state. The new
  // access token is still returned so a request already under way (such as
  // the logout call) can finish.
  let expiresAt = Date.now() + data.expiresIn * 1000
  if (generation === sessionGeneration()) {
    expiresAt = saveSession(data.accessToken, data.expiresIn, undefined, {
      persistent,
      refreshToken: data.refreshToken,
      refreshExpiresInSeconds: data.refreshExpiresIn,
    })
  }
  return { accessToken: data.accessToken, expiresAt, user: data.user }
}

/**
 * Sends a request to the API Gateway.
 *
 * Authenticated requests renew an expired access token first, and a 401 is
 * retried once after a refresh. Only when that fails is the session ended.
 * @param {string} path API path starting with "/", e.g. "/api/users/me".
 * @param {object} [options]
 * @param {string} [options.method='GET']
 * @param {*} [options.body] Serialized as JSON when provided.
 * @param {boolean} [options.auth=false] Attach the stored bearer token.
 * @param {AbortSignal} [options.signal]
 * @param {'json'|'text'} [options.responseType='json']
 * @returns {Promise<*>} Parsed JSON (or requested text) body, or null for an empty response.
 */
export function request(path, options = {}) {
  return send(path, options, true)
}

async function send(path, {
  method = 'GET',
  body,
  auth = false,
  signal,
  responseType = 'json',
} = {}, mayRefresh, refreshedToken = null) {
  const headers = { Accept: responseType === 'text' ? 'text/csv' : 'application/json' }

  if (auth) {
    let token = refreshedToken ?? getAccessToken()
    if (token === null && mayRefresh && canRefresh()) {
      token = (await refreshSession()).accessToken
      mayRefresh = false
    }
    if (token === null) {
      throw new ApiError(MESSAGES.noSession, {
        status: 401,
        kind: ApiErrorKind.UNAUTHENTICATED,
      })
    }
    headers.Authorization = `Bearer ${token}`
  }

  const init = { method, headers, signal }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    init.body = JSON.stringify(body)
  }

  let response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, init)
  } catch (error) {
    if (error?.name === 'AbortError') {
      throw error
    }
    throw new ApiError(MESSAGES.network, { kind: ApiErrorKind.NETWORK })
  }

  const data = response.ok && responseType === 'text'
    ? await response.text()
    : await readJson(response)
  if (!response.ok) {
    if (auth && response.status === 401) {
      if (mayRefresh && canRefresh()) {
        const { accessToken } = await refreshSession()
        return send(path, { method, body, auth, signal, responseType }, false, accessToken)
      }
      notifyUnauthorized()
    }
    throw errorFromResponse(response, data)
  }
  if (responseType === 'text') {
    return data
  }
  if (data === undefined) {
    throw new ApiError(MESSAGES.invalidResponse, {
      status: response.status,
      kind: ApiErrorKind.INVALID_RESPONSE,
    })
  }
  return data
}

export const apiClient = {
  get: (path, options) => request(path, { ...options, method: 'GET' }),
  post: (path, body, options) => request(path, { ...options, method: 'POST', body }),
  put: (path, body, options) => request(path, { ...options, method: 'PUT', body }),
  patch: (path, body, options) => request(path, { ...options, method: 'PATCH', body }),
  delete: (path, options) => request(path, { ...options, method: 'DELETE' }),
}

/**
 * Calls an entry from ENDPOINTS (services/endpoints.js). Planned endpoints are
 * never called: they reject with an ApiError of kind NOT_IMPLEMENTED.
 * @param {{method: string, path: string, available: boolean, auth?: boolean}} endpoint
 * @param {object} [options]
 * @param {object} [options.params] Values for `:name` path segments.
 * @param {*} [options.body]
 * @param {AbortSignal} [options.signal]
 */
export async function callEndpoint(endpoint, { params, body, signal } = {}) {
  if (!endpoint.available) {
    throw new ApiError(MESSAGES.notImplemented, {
      status: 501,
      kind: ApiErrorKind.NOT_IMPLEMENTED,
    })
  }
  return request(buildPath(endpoint.path, params), {
    method: endpoint.method,
    body,
    auth: Boolean(endpoint.auth),
    signal,
  })
}
