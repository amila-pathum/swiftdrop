import { NavLink } from 'react-router-dom'

const TYPES = [
  { to: '/register', label: 'Customer' },
  { to: '/register/merchant', label: 'Merchant' },
  { to: '/register/courier', label: 'Courier' },
]

/** Switches between the customer, merchant and courier sign-up pages. */
export default function RegistrationTypeTabs() {
  return (
    <nav className="segmented" aria-label="Account type">
      {TYPES.map(({ to, label }) => (
        <NavLink key={to} to={to} end className="segmented-item">
          {label}
        </NavLink>
      ))}
    </nav>
  )
}
