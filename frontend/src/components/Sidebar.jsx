import { useEffect, useState } from 'react'
import { NavLink } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { isActive, navigationFor } from '../utils/roles'
import MerchantNav, { GridIcon } from './MerchantNav'
import { merchantService } from '../services/merchantService'

const DISPATCH_LINKS = [
  { to: '/dispatch', label: 'Dispatch' },
  { to: '/dispatch/deliveries', label: 'Ongoing deliveries' },
  { to: '/dispatch/workload', label: 'Courier workload' },
  { to: '/reports', label: 'Reports' },
]

function ApprovalIcon() {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor"
      strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <polyline points="14 2 14 8 20 8" />
      <line x1="16" y1="13" x2="8" y2="13" />
      <line x1="16" y1="17" x2="8" y2="17" />
      <polyline points="10 9 9 9 8 9" />
    </svg>
  )
}

function UserIcon() {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor"
      strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
      <circle cx="12" cy="7" r="4" />
    </svg>
  )
}

function SidebarFooter() {
  return (
    <div className="sidebar-bottom">
      <div className="sidebar-help-card">
        <p className="sidebar-help-title">A little help goes a long way.</p>
        <p className="sidebar-help-desc">Our team is here for you.</p>
        <a href="#support" className="sidebar-help-link">
          <span className="sidebar-help-icon" aria-hidden="true">?</span> Contact support
        </a>
      </div>
      <div className="sidebar-copyright">© 2026 SwiftDrop</div>
    </div>
  )
}

export default function Sidebar({ open = false, onNavigate }) {
  const { user } = useAuth()
  const role = user?.role
  const status = user?.status
  const [businessName, setBusinessName] = useState('')

  useEffect(() => {
    let active = true
    if (role === 'MERCHANT' && status === 'ACTIVE') {
      merchantService.getMyMerchant()
        .then((merchant) => {
          if (active && merchant?.businessName) {
            setBusinessName(merchant.businessName)
          }
        })
        .catch(() => {
          // Keep the account name visible when merchant details are unavailable.
        })
    }
    return () => {
      active = false
    }
  }, [role, status])

  const sidebarClass = `sidebar${open ? ' sidebar-open' : ''}`

  if (user?.role === 'MERCHANT' && isActive(user)) {
    return (
      <nav id="app-sidebar" className={sidebarClass} aria-label="Main navigation">
        <div className="sidebar-workspace-badge">
          <span className="sidebar-workspace-label">MERCHANT WORKSPACE</span>
          <span className="sidebar-workspace-title">
            {businessName || user.name || 'Merchant Workspace'}
          </span>
        </div>
        <div className="sidebar-section-title">WORKSPACE</div>
        <MerchantNav onNavigate={onNavigate} />
        <SidebarFooter />
      </nav>
    )
  }

  if (user?.role === 'ADMIN' && isActive(user)) {
    const adminLinks = [
      { to: '/admin/dashboard', label: 'Overview', icon: GridIcon },
      { to: '/admin/merchant-approvals', label: 'Merchant Approvals', icon: ApprovalIcon },
      ...DISPATCH_LINKS,
      { to: '/profile', label: 'Profile', icon: UserIcon },
    ]

    return (
      <nav id="app-sidebar" className={sidebarClass} aria-label="Main navigation">
        <div className="sidebar-workspace-badge">
          <span className="sidebar-workspace-label">ADMIN WORKSPACE</span>
          <span className="sidebar-workspace-title">Operations console</span>
        </div>
        <div className="sidebar-section-title">WORKSPACE</div>
        <ul className="merchant-nav-list">
          {adminLinks.map(({ to, label, icon: Icon }) => (
            <li key={to}>
              <NavLink to={to} end={to === '/admin/dashboard' || to === '/dispatch'}
                className={({ isActive }) => (isActive ? 'active' : '')}
                onClick={onNavigate}>
                {Icon && <Icon />}
                <span>{label}</span>
              </NavLink>
            </li>
          ))}
        </ul>
        <SidebarFooter />
      </nav>
    )
  }

  const links = user?.role === 'DISPATCHER' && isActive(user)
    ? [...navigationFor(user), ...DISPATCH_LINKS]
    : navigationFor(user)

  return (
    <nav id="app-sidebar" className={sidebarClass} aria-label="Main navigation">
      <ul>
        {links.map(({ to, label }) => (
          <li key={to}>
            <NavLink to={to} end={to === '/dashboard' || to === '/dispatch'}
              onClick={onNavigate}>
              {label}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  )
}
