import { useAuth } from '../context/AuthContext'
import { roleLabel } from '../utils/roles'
import Button from './Button'
import Logo from './Logo'
import NotificationBell from './NotificationBell'

function MenuIcon() {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor"
      strokeWidth="2" strokeLinecap="round" aria-hidden="true">
      <path d="M4 6h16M4 12h16M4 18h16" />
    </svg>
  )
}

/**
 * Top bar of the signed-in and authentication layouts. The user menu appears
 * only when someone is signed in. Pass `onMenuClick` to show a menu button
 * that opens the sidebar on small screens.
 */
export default function Navbar({ onMenuClick, menuOpen = false }) {
  const { user, logout } = useAuth()

  return (
    <header className="navbar">
      <div className="navbar-start">
        {onMenuClick && (
          <button
            type="button"
            className="icon-button navbar-menu-button"
            aria-label="Open menu"
            aria-expanded={menuOpen}
            aria-controls="app-sidebar"
            onClick={onMenuClick}
          >
            <MenuIcon />
          </button>
        )}
        <Logo />
      </div>
      {user && (
        <div className="navbar-user">
          <NotificationBell />
          <span className="navbar-avatar" aria-hidden="true">
            {(user.name ?? '?').trim().charAt(0).toUpperCase()}
          </span>
          <span className="navbar-user-name">{user.name}</span>
          <span className="role-badge">{roleLabel(user.role)}</span>
          <Button variant="secondary" size="sm" onClick={logout}>Log out</Button>
        </div>
      )}
    </header>
  )
}
