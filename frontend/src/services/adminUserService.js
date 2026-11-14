import { callEndpoint } from './apiClient'
import { ENDPOINTS } from './endpoints'

/** Creates one ACTIVE dispatcher through the ADMIN-only Identity API. */
export function createDispatcher({ name, email, password }) {
  return callEndpoint(ENDPOINTS.createStaffUser, {
    body: { name: name.trim(), email: email.trim(), password, role: 'DISPATCHER' },
  })
}
