import { useEffect, useState } from 'react'
import { NavLink } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
<<<<<<< HEAD

// Shared by every role. Role-specific links are added here as features are built.
const BASE_LINKS = [
=======
import MerchantNav from './MerchantNav'
import { merchantService } from '../services/merchantService'

  function GridIcon() {
    return (
      <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <rect x="3" y="3" width="7" height="7" rx="1" />
        <rect x="14" y="3" width="7" height="7" rx="1" />
        <rect x="14" y="14" width="7" height="7" rx="1" />
        <rect x="3" y="14" width="7" height="7" rx="1" />
      </svg>
    )
  }

function ApprovalIcon() {
    return (
      <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
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
      <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
        <circle cx="12" cy="7" r="4" />
      </svg>
    )
  }

const DEFAULT_LINKS = [
>>>>>>> develop
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/profile', label: 'Profile' },
]

export default function Sidebar() {
  const { user } = useAuth()
<<<<<<< HEAD
  const links = ['DISPATCHER', 'ADMIN'].includes(user?.role)
    ? [...BASE_LINKS,
      { to: '/dispatch', label: 'Dispatch' },
      { to: '/dispatch/deliveries', label: 'Ongoing deliveries' },
      { to: '/dispatch/workload', label: 'Courier workload' },
      { to: '/reports', label: 'Reports' }]
    : BASE_LINKS

  return (
    <nav className="sidebar" aria-label="Main navigation">
      <ul>
        {links.map(({ to, label }) => (
          <li key={to}>
            <NavLink to={to} end={to === '/dispatch'}>{label}</NavLink>
=======
  const [businessName, setBusinessName] = useState('')

  useEffect(() => {
    let active = true
    if (user?.role === 'MERCHANT') {
      merchantService.getMyMerchant()
        .then((m) => {
          if (active && m?.businessName) {
            setBusinessName(m.businessName)
          }
        })
        .catch(() => {
          // If profile fetch fails or not found, fallback gracefully
        })
    }
    return () => {
      active = false
    }
  }, [user?.role])

  if (user?.role === 'MERCHANT') {
    return (
      <nav className="sidebar" aria-label="Main navigation">
        <div className="sidebar-workspace-badge">
          <span className="sidebar-workspace-label">MERCHANT WORKSPACE</span>
          <span className="sidebar-workspace-title">{businessName || user.name || 'Merchant Workspace'}</span>
        </div>
        <div className="sidebar-section-title">WORKSPACE</div>
        <MerchantNav />
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
      </nav>
    )
  }

  if (user?.role === 'ADMIN') {
    return (
      <nav className="sidebar" aria-label="Main navigation">
        <div className="sidebar-workspace-badge">
          <span className="sidebar-workspace-label">ADMIN WORKSPACE</span>
          <span className="sidebar-workspace-title">Operations console</span>
        </div>
        <div className="sidebar-section-title">WORKSPACE</div>
        <ul className="merchant-nav-list">
          <li>
            <NavLink to="/dashboard" end className={({ isActive }) => (isActive ? 'active' : '')}>
              <GridIcon />
              <span>Overview</span>
            </NavLink>
>>>>>>> develop
          </li>
          <li>
            <NavLink to="/admin/merchant-approvals" className={({ isActive }) => (isActive ? 'active' : '')}>
              <ApprovalIcon />
              <span>Merchant Approvals</span>
            </NavLink>
          </li>
          <li>
            <NavLink to="/profile" className={({ isActive }) => (isActive ? 'active' : '')}>
              <UserIcon />
              <span>Profile</span>
            </NavLink>
          </li>
        </ul>
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
      </nav>
    )
  }

  return (
    <nav id="app-sidebar" className={`sidebar${open ? ' sidebar-open' : ''}`}
      aria-label="Main navigation">
      <ul>
          {
            DEFAULT_LINKS.map(({ to, label }) => (
              <li key={to}>
                <NavLink to={to}>{label}</NavLink>
              </li>
            ))
          }
      </ul>
    </nav>
  )
}
