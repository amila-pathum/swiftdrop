import { Navigate, Route, Routes } from 'react-router-dom'
import AppLayout from './layouts/AppLayout'
import AuthLayout from './layouts/AuthLayout'
import PublicLayout from './layouts/PublicLayout'
import ProtectedRoute from './components/ProtectedRoute'
import PublicOnlyRoute from './components/PublicOnlyRoute'
import { useAuth } from './context/AuthContext'
import AdminDashboardPage from './pages/admin/AdminDashboardPage'
import ApplicationStatusPage from './pages/application/ApplicationStatusPage'
import ApplicationSubmittedPage from './pages/application/ApplicationSubmittedPage'
import CourierRegisterPage from './pages/auth/CourierRegisterPage'
import ForgotPasswordPage from './pages/auth/ForgotPasswordPage'
import GoogleCallbackPage from './pages/auth/GoogleCallbackPage'
import LoginPage from './pages/auth/LoginPage'
import MerchantRegisterPage from './pages/auth/MerchantRegisterPage'
import RegisterPage from './pages/auth/RegisterPage'
import ResetPasswordPage from './pages/auth/ResetPasswordPage'
import CourierDashboardPage from './pages/courier/CourierDashboardPage'
import CustomerDashboardPage from './pages/customer/CustomerDashboardPage'
import DispatcherDashboardPage from './pages/dispatcher/DispatcherDashboardPage'
import MerchantDashboardPage from './pages/merchant/MerchantDashboardPage'
import NotAvailablePage from './pages/NotAvailablePage'
import NotFoundPage from './pages/NotFoundPage'
import NotificationsPage from './pages/NotificationsPage'
import ProfilePage from './pages/ProfilePage'
import LandingPage from './pages/public/LandingPage'
import TrackParcelPage from './pages/public/TrackParcelPage'
import { homePathFor, ROLE_DASHBOARDS } from './utils/roles'

// Each role's dashboard, restricted to that role. Feature teams add their
// role-specific routes next to these, wrapped the same way.
const ROLE_PAGES = {
  CUSTOMER: <CustomerDashboardPage />,
  MERCHANT: <MerchantDashboardPage />,
  COURIER: <CourierDashboardPage />,
  DISPATCHER: <DispatcherDashboardPage />,
  ADMIN: <AdminDashboardPage />,
}

// /dashboard is the generic "home" target: it forwards to the role dashboard,
// or to the application status page for accounts that are not active.
function DashboardRedirect() {
  const { user } = useAuth()
  const home = homePathFor(user)
  return home === null
    ? <NotAvailablePage title="Dashboard not available" />
    : <Navigate to={home} replace />
}

function App() {
  return (
    <Routes>
      {/* Public marketing pages, visible to everyone. */}
      <Route element={<PublicLayout />}>
        <Route path="/" element={<LandingPage />} />
        <Route path="/track" element={<TrackParcelPage />} />
        <Route path="/track/:trackingNumber" element={<TrackParcelPage />} />
      </Route>

      {/* Signed-in users are sent on to their dashboard or application status. */}
      <Route element={<PublicOnlyRoute />}>
        <Route element={<AuthLayout />}>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        </Route>
        <Route element={<AuthLayout wide />}>
          <Route path="/register/merchant" element={<MerchantRegisterPage />} />
          <Route path="/register/courier" element={<CourierRegisterPage />} />
        </Route>
      </Route>

      <Route element={<AuthLayout />}>
        {/* Not public-only: it navigates on by itself once the code is exchanged. */}
        <Route path="/auth/google/callback" element={<GoogleCallbackPage />} />
        <Route path="/application/submitted" element={<ApplicationSubmittedPage />} />
        {/* Opened from a reset link, signed in or not. */}
        <Route path="/reset-password" element={<ResetPasswordPage />} />
      </Route>

      {/* Any signed-in user, whatever their account status. */}
      <Route element={<ProtectedRoute requireActive={false} />}>
        <Route element={<AuthLayout />}>
          <Route path="/application/status" element={<ApplicationStatusPage />} />
        </Route>
        <Route element={<AppLayout />}>
          <Route path="/dashboard" element={<DashboardRedirect />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/notifications" element={<NotificationsPage />} />
        </Route>
      </Route>

      {/* Active accounts only; each dashboard limited to its own role. */}
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          {Object.entries(ROLE_PAGES).map(([role, page]) => (
            <Route
              key={role}
              path={ROLE_DASHBOARDS[role]}
              element={<ProtectedRoute allowedRoles={[role]}>{page}</ProtectedRoute>}
            />
          ))}
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

export default App
