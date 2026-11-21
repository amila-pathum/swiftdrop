import { request } from './apiClient'

// Admin review of courier applications (Courier Service). Couriers register and
// apply themselves; an administrator only approves or rejects.
const BASE = '/api/couriers/admin/applications'

export const courierApplicationService = {
  /** Every application, newest first; `status` narrows to PENDING, APPROVED or REJECTED. */
  getAllApplications: (status, signal) => {
    const query = status ? `?status=${encodeURIComponent(status)}` : ''
    return request(`${BASE}${query}`, { auth: true, signal })
  },

  /** Approves the application, activates the login account and creates the courier profile. */
  approveApplication: (applicationId) =>
    request(`${BASE}/${encodeURIComponent(applicationId)}/approve`, {
      method: 'PATCH',
      auth: true,
    }),

  rejectApplication: (applicationId, reason) =>
    request(`${BASE}/${encodeURIComponent(applicationId)}/reject`, {
      method: 'PATCH',
      body: { reason },
      auth: true,
    }),
}
