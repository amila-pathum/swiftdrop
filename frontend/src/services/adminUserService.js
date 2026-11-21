import { callEndpoint } from './apiClient'
import { buildPath } from './endpoints'
import { ENDPOINTS } from './endpoints'

/** Roles an administrator may create. Customers, merchants and couriers register themselves. */
export const STAFF_ROLES = Object.freeze(['DISPATCHER', 'ADMIN'])

/**
 * Creates one ACTIVE dispatcher or administrator through the ADMIN-only
 * Identity API. Any other role is refused here, before a request is sent.
 */
export function createStaffAccount({ name, email, password, role }) {
  if (!STAFF_ROLES.includes(role)) {
    return Promise.reject(new Error('Only dispatcher and admin accounts can be created.'))
  }
  return callEndpoint(ENDPOINTS.createStaffUser, {
    body: { name: name.trim(), email: email.trim(), password, role },
  })
}

/** Creates one ACTIVE dispatcher through the ADMIN-only Identity API. */
export function createDispatcher({ name, email, password }) {
  return createStaffAccount({ name, email, password, role: 'DISPATCHER' })
}

/**
 * Lists users with optional search, role filter, and status filter.
 * @param {object} params
 * @param {string} [params.search]  Free-text search across name, email, phone.
 * @param {string} [params.role]    Role enum value, e.g. 'CUSTOMER'.
 * @param {string} [params.status]  Status enum value, e.g. 'ACTIVE'.
 * @param {number} [params.page]    0-based page index (default 0).
 * @param {number} [params.size]    Page size (default 10).
 * @param {AbortSignal} [signal]
 * @returns {Promise<{content: UserResponse[], totalPages: number, totalElements: number, number: number, size: number}>}
 */
export function listUsers({ search, role, status, page = 0, size = 10 } = {}, signal) {
  const qp = new URLSearchParams()
  if (search && search.trim()) qp.set('search', search.trim())
  if (role) qp.set('role', role)
  if (status) qp.set('status', status)
  qp.set('page', String(page))
  qp.set('size', String(size))
  qp.set('sort', 'createdAt,DESC')

  const path = `${buildPath(ENDPOINTS.listAdminUsers.path)}?${qp.toString()}`
  return callEndpoint({ ...ENDPOINTS.listAdminUsers, path }, { signal })
}

/**
 * Changes the account status of a user (ACTIVE, SUSPENDED, DISABLED).
 * @param {number} userId
 * @param {string} status
 */
export function changeUserStatus(userId, status) {
  return callEndpoint(ENDPOINTS.changeUserStatus, {
    params: { id: userId },
    body: { status },
  })
}
