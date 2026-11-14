// Roles, account statuses and where each kind of user belongs.
// This only drives navigation; the backend enforces every permission.

export const Roles = Object.freeze({
  CUSTOMER: 'CUSTOMER',
  MERCHANT: 'MERCHANT',
  COURIER: 'COURIER',
  DISPATCHER: 'DISPATCHER',
  ADMIN: 'ADMIN',
})

// Mirrors the Identity Service's UserStatus enum.
export const AccountStatus = Object.freeze({
  ACTIVE: 'ACTIVE',
  PENDING_APPROVAL: 'PENDING_APPROVAL',
  SUSPENDED: 'SUSPENDED',
  DISABLED: 'DISABLED',
})

export const ROLE_DASHBOARDS = Object.freeze({
  CUSTOMER: '/customer/dashboard',
  MERCHANT: '/merchant/dashboard',
  COURIER: '/courier/dashboard',
  DISPATCHER: '/dispatcher/dashboard',
  ADMIN: '/admin/dashboard',
})

export const APPLICATION_STATUS_PATH = '/application/status'

// Roles whose accounts need admin approval before they can be used.
export const APPROVAL_ROLES = Object.freeze([Roles.MERCHANT, Roles.COURIER])

export function isKnownRole(role) {
  return Object.hasOwn(ROLE_DASHBOARDS, role ?? '')
}

export function isActive(user) {
  return user?.status === AccountStatus.ACTIVE
}

export function requiresApproval(role) {
  return APPROVAL_ROLES.includes(role)
}

/** "MERCHANT" -> "Merchant". */
export function roleLabel(role) {
  return typeof role === 'string' && role !== ''
    ? role.charAt(0) + role.slice(1).toLowerCase()
    : ''
}

/**
 * Where a signed-in user's home is:
 * - an ACTIVE user with a known role -> their role dashboard
 * - anyone else (pending, suspended, disabled) -> the application status page,
 *   which explains the account state
 * - an unknown role -> null (the caller shows a "not available" page)
 */
export function homePathFor(user) {
  if (!user || !isKnownRole(user.role)) return null
  return isActive(user) ? ROLE_DASHBOARDS[user.role] : APPLICATION_STATUS_PATH
}

// Sidebar links per role. Teams add entries here as their pages are built.
const COMMON_LINKS = [{ to: '/profile', label: 'Profile' }]

const ROLE_LINKS = {
  CUSTOMER: [{ to: '/customer/deliveries', label: 'My Deliveries' }],
  MERCHANT: [],
  COURIER: [],
  DISPATCHER: [],
  ADMIN: [],
}

/**
 * Navigation entries for the signed-in user: their dashboard, role-specific
 * links (only once the account is active) and shared links.
 * @returns {{to: string, label: string}[]}
 */
export function navigationFor(user) {
  if (!user) return []
  const home = homePathFor(user)
  const homeLink = home === null ? [] : [{
    to: home,
    label: home === APPLICATION_STATUS_PATH ? 'Application status' : 'Dashboard',
  }]
  const roleLinks = isActive(user) ? (ROLE_LINKS[user.role] ?? []) : []
  return [...homeLink, ...roleLinks, ...COMMON_LINKS]
}
