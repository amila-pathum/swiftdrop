// Reusable field validators. Each returns an error message, or null when valid.
// Limits mirror the Identity Service request DTOs.

// Matches the backend's lenient @Email check: something@something, no spaces.
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+$/
// Digits with optional leading +, spaces, dashes and brackets; 9 to 15 digits.
const PHONE_CHARS = /^\+?[\d\s()-]+$/

export const LIMITS = Object.freeze({
  NAME_MAX: 100,
  EMAIL_MAX: 255,
  PASSWORD_MIN: 8,
  // BCrypt only accepts 72 bytes, so multi-byte characters count more than once.
  PASSWORD_MAX: 72,
  ADDRESS_MAX: 255,
  TEXT_MAX: 100,
})

export function passwordTooLong(password) {
  return password.length > LIMITS.PASSWORD_MAX
    || new TextEncoder().encode(password).length > LIMITS.PASSWORD_MAX
}

/** Required text with a maximum length. `label` is used in messages, e.g. "your full name". */
export function validateRequiredText(value, { emptyMessage, max = LIMITS.TEXT_MAX, label }) {
  const trimmed = (value ?? '').trim()
  if (trimmed === '') return emptyMessage
  if (trimmed.length > max) return `${label} must be ${max} characters or fewer.`
  return null
}

export function validateEmail(email, { emptyMessage = 'Enter your email address.' } = {}) {
  const value = (email ?? '').trim()
  if (value === '') return emptyMessage
  if (value.length > LIMITS.EMAIL_MAX) return `Email must be ${LIMITS.EMAIL_MAX} characters or fewer.`
  if (!EMAIL_PATTERN.test(value)) return 'Enter a valid email address.'
  return null
}

export function validatePhone(phone, { emptyMessage = 'Enter your phone number.' } = {}) {
  const value = (phone ?? '').trim()
  if (value === '') return emptyMessage
  const digits = value.replace(/\D/g, '').length
  if (!PHONE_CHARS.test(value) || digits < 9 || digits > 15) {
    return 'Enter a valid phone number, for example 077 123 4567.'
  }
  return null
}

export function validateNewPassword(password) {
  if ((password ?? '').trim() === '') return 'Enter a password.'
  if (password.length < LIMITS.PASSWORD_MIN) {
    return `Password must be at least ${LIMITS.PASSWORD_MIN} characters.`
  }
  if (passwordTooLong(password)) return `Password must be ${LIMITS.PASSWORD_MAX} characters or fewer.`
  return null
}

export function validatePasswordConfirmation(confirmPassword, password) {
  if (confirmPassword === '' || confirmPassword === undefined) return 'Confirm your password.'
  if (confirmPassword !== password) return 'Passwords do not match.'
  return null
}

/** Builds { field: message } from [field, message] pairs, keeping field order. */
export function collectErrors(entries) {
  return Object.fromEntries(entries.filter(([, message]) => message !== null))
}
