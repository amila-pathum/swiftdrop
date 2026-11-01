import { ApiErrorKind } from '../../services/apiClient'

// Rules mirror the Identity Service request DTOs (LoginRequest, RegisterRequest).
// Each validator returns { field: message } in field order; empty means valid.

// Matches the backend's lenient @Email check: something@something, no spaces.
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+$/
const NAME_MAX = 100
const EMAIL_MAX = 255
const PASSWORD_MIN = 8
// BCrypt only accepts 72 bytes, so multi-byte characters count more than once.
const PASSWORD_MAX = 72

export const SERVICE_UNAVAILABLE_MESSAGE =
  'SwiftDrop service is temporarily unavailable. Please try again shortly.'

export function isServiceUnavailable(error) {
  return error?.kind === ApiErrorKind.NETWORK
    || error?.kind === ApiErrorKind.SERVICE_UNAVAILABLE
}

function passwordTooLong(password) {
  return password.length > PASSWORD_MAX
    || new TextEncoder().encode(password).length > PASSWORD_MAX
}

function validateEmail(email) {
  const value = email.trim()
  if (value === '') return 'Enter your email address.'
  if (value.length > EMAIL_MAX) return `Email must be ${EMAIL_MAX} characters or fewer.`
  if (!EMAIL_PATTERN.test(value)) return 'Enter a valid email address.'
  return null
}

function collect(entries) {
  return Object.fromEntries(entries.filter(([, message]) => message !== null))
}

export function validateLogin({ email, password }) {
  return collect([
    ['email', validateEmail(email)],
    ['password',
      password.trim() === '' ? 'Enter your password.'
        : passwordTooLong(password) ? `Password must be ${PASSWORD_MAX} characters or fewer.`
          : null],
  ])
}

export function validateRegistration({ name, email, password, confirmPassword }) {
  const trimmedName = name.trim()
  return collect([
    ['name',
      trimmedName === '' ? 'Enter your full name.'
        : trimmedName.length > NAME_MAX ? `Name must be ${NAME_MAX} characters or fewer.`
          : null],
    ['email', validateEmail(email)],
    ['password',
      password.trim() === '' ? 'Enter a password.'
        : password.length < PASSWORD_MIN ? `Password must be at least ${PASSWORD_MIN} characters.`
          : passwordTooLong(password) ? `Password must be ${PASSWORD_MAX} characters or fewer.`
            : null],
    ['confirmPassword',
      confirmPassword === '' ? 'Confirm your password.'
        : confirmPassword !== password ? 'Passwords do not match.'
          : null],
  ])
}
