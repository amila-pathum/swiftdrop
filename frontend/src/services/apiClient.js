import { API_BASE_URL } from './config'
import { buildPath } from './endpoints'
import { getAccessToken } from './tokenStorage'

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

// Listeners told when an authenticated request is rejected with 401, i.e. the
// server no longer accepts the stored token. AuthContext ends the session.
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

/**
 * Sends a request to the API Gateway.
 * @param {string} path API path starting with "/", e.g. "/api/users/me".
 * @param {object} [options]
 * @param {string} [options.method='GET']
 * @param {*} [options.body] Serialized as JSON when provided.
 * @param {boolean} [options.auth=false] Attach the stored bearer token.
 * @param {AbortSignal} [options.signal]
 * @returns {Promise<*>} Parsed JSON body, or null for an empty response.
 */
export async function request(path, { method = 'GET', body, auth = false, signal } = {}) {
  const headers = { Accept: 'application/json' }

  if (auth) {
    const token = getAccessToken()
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

  const data = await readJson(response)
  if (!response.ok) {
    if (auth && response.status === 401) {
      unauthorizedListeners.forEach((listener) => listener())
    }
    throw errorFromResponse(response, data)
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
