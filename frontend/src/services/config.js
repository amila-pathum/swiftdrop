const DEFAULT_API_BASE_URL = 'http://localhost:8080'

// All API traffic goes through the API Gateway, never directly to a service.
export function resolveApiBaseUrl(value) {
  const url = value?.trim() || DEFAULT_API_BASE_URL
  return url.replace(/\/+$/, '')
}

export const API_BASE_URL = resolveApiBaseUrl(import.meta.env.VITE_API_BASE_URL)

// Google sign-in is shown only when explicitly enabled; the backend must also have
// Google configured. Read at call time so tests can change it.
export function isGoogleLoginEnabled(env = import.meta.env) {
  return env.VITE_GOOGLE_LOGIN_ENABLED === 'true'
}
