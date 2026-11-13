import { Outlet } from 'react-router-dom'
import Navbar from '../components/Navbar'

/**
 * Navbar with content centred below it. Used by sign-in, registration and
 * full-page status screens. Works as a layout route or with children.
 * `wide` gives multi-section forms (merchant and courier sign-up) more room.
 */
export default function AuthLayout({ children, wide = false }) {
  return (
    <div className="auth-shell">
      <Navbar />
      <main className={`auth-main${wide ? ' auth-main-wide' : ''}`}>
        {children ?? <Outlet />}
      </main>
      <p className="auth-legal">© {new Date().getFullYear()} SwiftDrop · Courier management platform</p>
    </div>
  )
}
