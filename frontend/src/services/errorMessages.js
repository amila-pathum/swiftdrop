import { ApiError, ApiErrorKind } from './apiClient'

// Centralized, user-safe error messages. Pages describe only what is specific
// to them (e.g. what a 401 means on the login form); everything else falls
// back to the shared messages here.

export const SERVICE_UNAVAILABLE_MESSAGE =
  'SwiftDrop service is temporarily unavailable. Please try again shortly.'
export const GENERIC_ERROR_MESSAGE = 'Something went wrong. Please try again.'
// Login returns 403 for accounts that are not ACTIVE, including merchants and
// couriers whose applications have not been approved yet.
export const ACCOUNT_NOT_ACTIVE_MESSAGE = 'This account is not active. If you applied as a '
  + 'merchant or courier, your application may still be awaiting admin approval.'
export const STORAGE_ERROR_MESSAGE =
  'Unable to sign in. Check that your browser allows this site to store data.'

export function isServiceUnavailable(error) {
  return error?.kind === ApiErrorKind.NETWORK
    || error?.kind === ApiErrorKind.SERVICE_UNAVAILABLE
}

export function isNotImplemented(error) {
  return error?.kind === ApiErrorKind.NOT_IMPLEMENTED
}

/**
 * Turns any thrown value into a message safe to show to users.
 *
 * @param {*} error
 * @param {object} [options]
 * @param {Object<number, string>} [options.byStatus] Messages for specific HTTP statuses.
 * @param {string} [options.notImplemented] Message for a planned backend endpoint.
 * @param {string} [options.nonApi] Message for errors not raised by the API client
 *   (e.g. blocked browser storage).
 * @param {string} [options.fallback]
 */
export function describeError(error, {
  byStatus = {},
  notImplemented,
  nonApi,
  fallback = GENERIC_ERROR_MESSAGE,
} = {}) {
  if (isServiceUnavailable(error)) return SERVICE_UNAVAILABLE_MESSAGE
  if (isNotImplemented(error)) return notImplemented ?? error.message
  if (!(error instanceof ApiError)) return nonApi ?? fallback
  return byStatus[error.status] ?? fallback
}
