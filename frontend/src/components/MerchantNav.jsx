import { NavLink } from 'react-router-dom'

export function GridIcon() {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="3" y="3" width="7" height="7" rx="1" />
      <rect x="14" y="3" width="7" height="7" rx="1" />
      <rect x="14" y="14" width="7" height="7" rx="1" />
      <rect x="3" y="14" width="7" height="7" rx="1" />
    </svg>
  )
}

export function BoxIcon() {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" />
      <polyline points="3.27 6.96 12 12.01 20.73 6.96" />
      <line x1="12" y1="22.08" x2="12" y2="12" />
    </svg>
  )
}

export function KeyIcon() {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="7.5" cy="15.5" r="5.5" />
      <path d="M11.5 11.5L22 1" />
      <path d="M18 5l3 3" />
      <path d="M15 8l2 2" />
    </svg>
  )
}

export function WebhookIcon() {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M18 16.98h-5.99c-1.1 0-1.95.94-2.48 1.9A4 4 0 0 1 2 17c0-2.21 1.79-4 4-4h.5c.34 0 .66-.08.96-.22.61-.31 1.04-.92 1.04-1.63V7.02" />
      <circle cx="18" cy="17" r="3" />
      <circle cx="6" cy="17" r="3" />
      <circle cx="9.5" cy="5" r="3" />
    </svg>
  )
}

export function BusinessIcon() {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="2" y="7" width="20" height="14" rx="2" ry="2" />
      <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16" />
    </svg>
  )
}

const MERCHANT_LINKS = [
  { to: '/dashboard', label: 'Overview', icon: GridIcon },
  { to: '/merchant/orders', label: 'Orders', icon: BoxIcon },
  { to: '/merchant/api-keys', label: 'API keys', icon: KeyIcon },
  { to: '/merchant/webhooks', label: 'Webhook settings', icon: WebhookIcon },
  { to: '/profile', label: 'Business profile', icon: BusinessIcon },
]

export default function MerchantNav({ className = '' }) {
  return (
    <ul className={`merchant-nav-list ${className}`.trim()}>
      {MERCHANT_LINKS.map(({ to, label, icon: Icon }) => (
        <li key={to}>
          <NavLink
            to={to}
            end={to === '/dashboard'}
            className={({ isActive }) => (isActive ? 'active' : '')}
          >
            <Icon />
            <span>{label}</span>
          </NavLink>
        </li>
      ))}
    </ul>
  )
}

