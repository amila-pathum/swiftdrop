import { NavLink } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

// Shared by every role. Role-specific links are added here as features are built.
const BASE_LINKS = [
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/profile', label: 'Profile' },
]

export default function Sidebar() {
  const { user } = useAuth()
  const links = ['DISPATCHER', 'ADMIN'].includes(user?.role)
    ? [...BASE_LINKS, { to: '/dispatch', label: 'Dispatch' }, { to: '/reports', label: 'Reports' }]
    : BASE_LINKS

  return (
    <nav className="sidebar" aria-label="Main navigation">
      <ul>
        {links.map(({ to, label }) => (
          <li key={to}>
            <NavLink to={to}>{label}</NavLink>
          </li>
        ))}
      </ul>
    </nav>
  )
}
