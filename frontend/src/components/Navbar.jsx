import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import Button from './Button'

function formatRole(role) {
  return role ? role.charAt(0) + role.slice(1).toLowerCase() : ''
}

function Logo() {
  return (
    <span className="navbar-logo" aria-hidden="true">
      <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor"
        strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M21 8l-9-5-9 5v8l9 5 9-5z" />
        <path d="M3 8l9 5 9-5" />
        <path d="M12 13v8" />
      </svg>
    </span>
  )
}

// Shown on every page. The user menu appears only when someone is signed in.
export default function Navbar() {
  const { user, logout } = useAuth()

  return (
    <header className="navbar">
      <Link to="/" className="navbar-brand">
        <Logo />
        SwiftDrop
      </Link>
      {user && (
        <div className="navbar-user">
          <span className="navbar-user-name">{user.name}</span>
          <span className="role-badge">{formatRole(user.role)}</span>
          <Button variant="secondary" onClick={logout}>Log out</Button>
        </div>
      )}
    </header>
  )
}
