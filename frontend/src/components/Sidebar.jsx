import { useEffect, useState } from 'react'
import { NavLink } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { isActive, navigationFor, ROLE_DASHBOARDS } from '../utils/roles'
import MerchantNav, { BoxIcon, GridIcon } from './MerchantNav'
import { merchantService } from '../services/merchantService'

const svg = (children) => function Icon() {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor"
      strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{children}</svg>
  )
}
const SendIcon = svg(<><path d="m22 2-7 20-4-9-9-4Z" /><path d="M22 2 11 13" /></>)
const TruckIcon = svg(<><rect x="1" y="3" width="15" height="13" /><path d="M16 8h4l3 3v5h-7Z" /><circle cx="5.5" cy="18.5" r="2.5" /><circle cx="18.5" cy="18.5" r="2.5" /></>)
const ChartIcon = svg(<><path d="M3 3v18h18" /><path d="M7 16v-5" /><path d="M12 16V8" /><path d="M17 16v-9" /></>)
const PlusIcon = svg(<><circle cx="12" cy="12" r="10" /><path d="M12 8v8" /><path d="M8 12h8" /></>)
const StatusIcon = svg(<><circle cx="12" cy="12" r="10" /><path d="M12 6v6l4 2" /></>)

const DISPATCH_LINKS = [
  { to: '/dispatch', label: 'Dispatch', icon: SendIcon },
  { to: '/dispatch/deliveries', label: 'Ongoing deliveries', icon: TruckIcon },
  { to: '/dispatch/workload', label: 'Courier workload', icon: UsersIcon },
  { to: '/reports', label: 'Reports', icon: ChartIcon },
]

// Icons for the shared role links (utils/roles navigationFor).
const LINK_ICONS = {
  '/customer/create-delivery': PlusIcon,
  '/customer/deliveries': BoxIcon,
  '/courier/deliveries': BoxIcon,
  '/courier/profile': UserIcon,
  '/profile': UserIcon,
  '/application/status': StatusIcon,
}
const WORKSPACES = {
  CUSTOMER: ['CUSTOMER ACCOUNT', 'Shipments'],
  COURIER: ['COURIER WORKSPACE', 'Deliveries'],
  DISPATCHER: ['DISPATCH WORKSPACE', 'Dispatch centre'],
}

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

function UsersIcon() {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor"
      strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
      <circle cx="9" cy="7" r="4" />
      <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
      <path d="M16 3.13a4 4 0 0 1 0 7.75" />
    </svg>
  )
}

function ZoneIcon() {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor"
      strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" />
      <circle cx="12" cy="10" r="3" />
    </svg>
  )
}

function PricingIcon() {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor"
      strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <line x1="12" y1="1" x2="12" y2="23" />
      <path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" />
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
      </nav>
    )
  }

  if (user?.role === 'ADMIN' && isActive(user)) {
    const adminLinks = [
      { to: '/admin/dashboard', label: 'Overview', icon: GridIcon },
      { to: '/admin/users', label: 'User Management', icon: UsersIcon },
      { to: '/admin/merchant-approvals', label: 'Merchant Approvals', icon: ApprovalIcon },
      { to: '/admin/courier-approvals', label: 'Courier Approvals', icon: ApprovalIcon },
      { to: '/admin/zones', label: 'Delivery Zones', icon: ZoneIcon },
      { to: '/admin/pricing', label: 'Pricing', icon: PricingIcon },
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

  const base = navigationFor(user)
  // The dispatcher dashboard is the /dispatch page, so it is not listed twice.
  const links = user?.role === 'DISPATCHER' && isActive(user)
    ? [...base.slice(0, 1), ...DISPATCH_LINKS.filter(({ to }) => to !== '/dispatch'), ...base.slice(1)]
    : base
  const [workspaceLabel, workspaceTitle] = (isActive(user) && WORKSPACES[role]) || []

  return (
    <nav id="app-sidebar" className={sidebarClass} aria-label="Main navigation">
      {workspaceLabel && (
        <div className="sidebar-workspace-badge">
          <span className="sidebar-workspace-label">{workspaceLabel}</span>
          <span className="sidebar-workspace-title">{workspaceTitle}</span>
        </div>
      )}
      <div className="sidebar-section-title">WORKSPACE</div>
      <ul className="merchant-nav-list">
        {links.map(({ to, label, icon }) => {
          const Icon = icon || LINK_ICONS[to] || (Object.values(ROLE_DASHBOARDS).includes(to) ? GridIcon : null)
          return (
            <li key={to}>
              <NavLink to={to} end={to === '/dashboard' || to === '/dispatch' || to === '/courier/dashboard'}
                className={({ isActive: current }) => (current ? 'active' : '')} onClick={onNavigate}>
                {Icon && <Icon />}
                <span>{label}</span>
              </NavLink>
            </li>
          )
        })}
      </ul>
      <SidebarFooter />
    </nav>
  )
}
