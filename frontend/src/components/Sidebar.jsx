import { NavLink } from 'react-router-dom'

// Shared by every role. Role-specific links are added here as features are built.
const LINKS = [
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/profile', label: 'Profile' },
]

export default function Sidebar() {
  return (
    <nav className="sidebar" aria-label="Main navigation">
      <ul>
        {LINKS.map(({ to, label }) => (
          <li key={to}>
            <NavLink to={to}>{label}</NavLink>
          </li>
        ))}
      </ul>
    </nav>
  )
}
