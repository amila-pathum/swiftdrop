import { Outlet } from 'react-router-dom'
import Navbar from '../components/Navbar'

// Navbar with content centred below it. Used by sign-in, registration and
// full-page status screens. Works as a layout route or with children.
export default function AuthLayout({ children }) {
  return (
    <div className="auth-shell">
      <Navbar />
      <main className="auth-main">
        {children ?? <Outlet />}
      </main>
    </div>
  )
}
