import { NavLink } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { navigationFor } from '../utils/roles'

/**
 * Role-aware navigation for the signed-in layout. Links come from
 * navigationFor() in utils/roles.js; add role-specific links there.
 */
export default function Sidebar({ open = false, onNavigate }) {
  const { user } = useAuth()
  const links = navigationFor(user)

  return (
    <nav id="app-sidebar" className={`sidebar${open ? ' sidebar-open' : ''}`}
      aria-label="Main navigation">
      <ul>
        {links.map(({ to, label }) => (
          <li key={to}>
            <NavLink to={to} onClick={onNavigate}>{label}</NavLink>
          </li>
        ))}
      </ul>
    </nav>
  )
}
