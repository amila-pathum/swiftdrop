import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { postLoginPath } from '../utils/postLoginPath'
import { AuthPendingScreen } from './ProtectedRoute'

// Pages such as login and registration, which signed-in users should not see.
// This is also what navigates the user onward after a successful login.
export default function PublicOnlyRoute({ children }) {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'loading') {
    return <AuthPendingScreen />
  }
  if (status === 'authenticated') {
    return <Navigate to={postLoginPath(location.state?.from)} replace />
  }
  return children ?? <Outlet />
}
