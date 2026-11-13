import { Navigate, Route, Routes } from 'react-router-dom'
import AppLayout from './layouts/AppLayout'
import AuthLayout from './layouts/AuthLayout'
import ProtectedRoute from './components/ProtectedRoute'
import PublicOnlyRoute from './components/PublicOnlyRoute'
import { useAuth } from './context/AuthContext'
import AdminDashboardPage from './pages/admin/AdminDashboardPage'
import MerchantApprovalsPage from './pages/admin/MerchantApprovalsPage'
import GoogleCallbackPage from './pages/auth/GoogleCallbackPage'
import LoginPage from './pages/auth/LoginPage'
import RegisterPage from './pages/auth/RegisterPage'
import CourierDashboardPage from './pages/courier/CourierDashboardPage'
import CustomerDashboardPage from './pages/customer/CustomerDashboardPage'
import DispatcherDashboardPage from './pages/dispatcher/DispatcherDashboardPage'
import MerchantDashboardPage from './pages/merchant/MerchantDashboardPage'
import MerchantApiKeysPage from './pages/merchant/MerchantApiKeysPage'
import MerchantWebhookPage from './pages/merchant/MerchantWebhookPage'
import NotAvailablePage from './pages/NotAvailablePage'
import NotFoundPage from './pages/NotFoundPage'
import ProfilePage from './pages/ProfilePage'

const DASHBOARDS = {
  CUSTOMER: <CustomerDashboardPage />,
  MERCHANT: <MerchantDashboardPage />,
  COURIER: <CourierDashboardPage />,
  DISPATCHER: <DispatcherDashboardPage />,
  ADMIN: <AdminDashboardPage />,
}

function RoleDashboard() {
  const { user } = useAuth()
  return DASHBOARDS[user?.role] ?? <NotAvailablePage title="Dashboard not available" />
}

function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/dashboard" replace />} />

      <Route element={<PublicOnlyRoute />}>
        <Route element={<AuthLayout />}>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
        </Route>
      </Route>

      {/* Not public-only: it navigates on by itself once the code is exchanged. */}
      <Route element={<AuthLayout />}>
        <Route path="/auth/google/callback" element={<GoogleCallbackPage />} />
      </Route>

      {/* Common protected routes */}
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route path="/dashboard" element={<RoleDashboard />} />
          <Route path="/profile" element={<ProfilePage />} />
        </Route>
      </Route>

      {/* Merchant protected routes */}
      <Route element={<ProtectedRoute allowedRoles={['MERCHANT']} />}>
        <Route element={<AppLayout />}>
          <Route path="/merchant/dashboard" element={<Navigate to="/dashboard" replace />} />
          <Route path="/merchant/api-keys" element={<MerchantApiKeysPage />} />
          <Route path="/merchant/webhooks" element={<MerchantWebhookPage />} />
          <Route path="/merchant/webhook" element={<Navigate to="/merchant/webhooks" replace />} />
        </Route>
      </Route>

      {/* Admin protected routes */}
      <Route element={<ProtectedRoute allowedRoles={['ADMIN']} />}>
        <Route element={<AppLayout />}>
          <Route path="/admin/merchant-approvals" element={<MerchantApprovalsPage />} />
          <Route path="/admin/approvals" element={<Navigate to="/admin/merchant-approvals" replace />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

export default App
