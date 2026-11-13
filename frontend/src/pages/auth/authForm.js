// Validation rules for the authentication forms. Each validator returns
// { field: message } in field order; empty means valid.
import {
  collectErrors,
  LIMITS,
  passwordTooLong,
  validateEmail,
  validateNewPassword,
  validatePasswordConfirmation,
  validatePhone,
  validateRequiredText,
} from '../../utils/validation'

// Kept here for existing imports; the shared versions live in services/errorMessages.
export { isServiceUnavailable, SERVICE_UNAVAILABLE_MESSAGE } from '../../services/errorMessages'

const fullName = (value) => validateRequiredText(value, {
  emptyMessage: 'Enter your full name.',
  max: LIMITS.NAME_MAX,
  label: 'Name',
})

const address = (value, emptyMessage = 'Enter your address.') => validateRequiredText(value, {
  emptyMessage,
  max: LIMITS.ADDRESS_MAX,
  label: 'Address',
})

export function validateLogin({ email, password }) {
  return collectErrors([
    ['email', validateEmail(email)],
    ['password',
      password.trim() === '' ? 'Enter your password.'
        : passwordTooLong(password) ? `Password must be ${LIMITS.PASSWORD_MAX} characters or fewer.`
          : null],
  ])
}

export function validateForgotPassword({ email }) {
  return collectErrors([['email', validateEmail(email)]])
}

export function validateNewPasswordForm({ password, confirmPassword }) {
  return collectErrors([
    ['password', validateNewPassword(password)],
    ['confirmPassword', validatePasswordConfirmation(confirmPassword, password)],
  ])
}

export function validateRegistration({ name, email, phone, address: addr, password, confirmPassword }) {
  return collectErrors([
    ['name', fullName(name)],
    ['email', validateEmail(email)],
    ['phone', validatePhone(phone)],
    ['address', address(addr)],
    ['password', validateNewPassword(password)],
    ['confirmPassword', validatePasswordConfirmation(confirmPassword, password)],
  ])
}

export function validateMerchantRegistration(values) {
  return collectErrors([
    ['businessName', validateRequiredText(values.businessName, {
      emptyMessage: 'Enter your business name.', max: LIMITS.NAME_MAX, label: 'Business name',
    })],
    ['businessRegistrationNumber', validateRequiredText(values.businessRegistrationNumber, {
      emptyMessage: 'Enter your business registration number.', max: 50, label: 'Registration number',
    })],
    ['businessEmail', validateEmail(values.businessEmail, {
      emptyMessage: 'Enter your business email address.',
    })],
    ['businessPhone', validatePhone(values.businessPhone, {
      emptyMessage: 'Enter your business phone number.',
    })],
    ['businessAddress', address(values.businessAddress, 'Enter your business address.')],
    ['contactName', validateRequiredText(values.contactName, {
      emptyMessage: "Enter the contact person's name.", max: LIMITS.NAME_MAX, label: 'Name',
    })],
    ['contactEmail', validateEmail(values.contactEmail, {
      emptyMessage: "Enter the contact person's email address.",
    })],
    ['password', validateNewPassword(values.password)],
    ['confirmPassword', validatePasswordConfirmation(values.confirmPassword, values.password)],
  ])
}

export function validateCourierRegistration(values, vehicleTypes = []) {
  const allowedVehicles = vehicleTypes.map((type) => type.value)
  return collectErrors([
    ['name', fullName(values.name)],
    ['email', validateEmail(values.email)],
    ['phone', validatePhone(values.phone)],
    ['address', address(values.address)],
    ['vehicleType',
      !values.vehicleType ? 'Select your vehicle type.'
        : allowedVehicles.length > 0 && !allowedVehicles.includes(values.vehicleType)
          ? 'Select a vehicle type from the list.'
          : null],
    ['vehicleRegistrationNumber', validateRequiredText(values.vehicleRegistrationNumber, {
      emptyMessage: 'Enter your vehicle registration number.', max: 20, label: 'Registration number',
    })],
    ['preferredArea', validateRequiredText(values.preferredArea, {
      emptyMessage: 'Enter your preferred delivery area.', max: LIMITS.TEXT_MAX, label: 'Delivery area',
    })],
    ['password', validateNewPassword(values.password)],
    ['confirmPassword', validatePasswordConfirmation(values.confirmPassword, values.password)],
  ])
}
