import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import AuthLayout from '../layouts/AuthLayout'
import { APPLICATION_STATUS_PATH, isActive } from '../utils/roles'
import Button from './Button'
import LoadingSpinner from './LoadingSpinner'

/**
 * Shown while the session is being verified. If verification failed because the
 * server was unreachable, the stored session is kept and the user can retry,
 * rather than being sent to the login page.
 */
export function AuthPendingScreen() {
  const { error, refreshUser } = useAuth()

  if (error) {
    return (
      <AuthLayout>
        <section className="card status-card" role="alert">
          <h1 className="card-title">Unable to reach SwiftDrop</h1>
          <p className="card-subtitle">{error.message}</p>
          <Button fullWidth onClick={() => { refreshUser() }}>Try again</Button>
        </section>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout>
      <div className="card status-card status-card-loading">
        <LoadingSpinner />
        <p role="status">Checking your session…</p>
      </div>
    </AuthLayout>
  )
}

/**
 * Restricts routes to signed-in users.
 *
 * - Anonymous users go to /login (and come back here after signing in).
 * - `allowedRoles`: other roles are sent to /dashboard, which resolves to their own home.
 * - `requireActive` (default true): accounts that are not ACTIVE (e.g. a
 *   merchant or courier awaiting approval) are sent to the application status page.
 *   Use requireActive={false} for pages any signed-in user may see (profile, status).
 *
 * This only controls navigation; the backend remains the security boundary.
 * Never put `allowedRoles` on /dashboard, which is the redirect target.
 */
export default function ProtectedRoute({ allowedRoles, requireActive = true, children }) {
  const { status, user } = useAuth()
  const location = useLocation()

  if (status === 'loading') {
    return <AuthPendingScreen />
  }
  if (status !== 'authenticated') {
    return <Navigate to="/login" replace state={{ from: location }} />
  }
  if (allowedRoles && !allowedRoles.includes(user?.role)) {
    return <Navigate to="/dashboard" replace />
  }
  if (requireActive && !isActive(user)) {
    return <Navigate to={APPLICATION_STATUS_PATH} replace />
  }
  return children ?? <Outlet />
}
