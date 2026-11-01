import { API_BASE_URL } from './config'
import { getAccessToken } from './tokenStorage'

export const ApiErrorKind = Object.freeze({
  NETWORK: 'NETWORK',
  SERVICE_UNAVAILABLE: 'SERVICE_UNAVAILABLE',
  UNAUTHENTICATED: 'UNAUTHENTICATED',
  HTTP: 'HTTP',
  INVALID_RESPONSE: 'INVALID_RESPONSE',
})

const UNAVAILABLE_STATUSES = new Set([502, 503, 504])

const MESSAGES = {
  network: 'Unable to reach the server. Check your connection and try again.',
  unavailable: 'The service is temporarily unavailable. Please try again shortly.',
  noSession: 'Please sign in to continue.',
  invalidResponse: 'The server returned an unexpected response.',
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
}
