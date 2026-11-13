import { ApplicationStatus } from '../services/applicationService'
import { AccountStatus, isActive } from './roles'

/**
 * Which application status view to show: 'approved' | 'pending' | 'rejected' | 'inactive'.
 *
 * Uses the application record from the Merchant/Courier Service when it is
 * available, otherwise the account status from /api/users/me. A rejection is
 * shown only when the backend reports one; the frontend never decides it.
 */
export function resolveApplicationView(user, application) {
  if (application?.status === ApplicationStatus.REJECTED) return 'rejected'
  if (isActive(user)) return 'approved'
  if (user?.status === AccountStatus.PENDING_APPROVAL
      || application?.status === ApplicationStatus.PENDING
      || application?.status === ApplicationStatus.APPROVED) {
    return 'pending'
  }
  return 'inactive'
}
