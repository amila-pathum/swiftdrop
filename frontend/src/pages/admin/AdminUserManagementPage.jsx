import { useCallback, useEffect, useRef, useState } from 'react'
import { useAuth } from '../../context/AuthContext'
import { describeError } from '../../services/errorMessages'
import {
  changeUserStatus,
  createStaffAccount,
  listUsers,
} from '../../services/adminUserService'
import Button from '../../components/Button'
import Input from '../../components/Input'
import LoadingSpinner from '../../components/LoadingSpinner'

// ─── constants ───────────────────────────────────────────────────────────────

const PAGE_SIZE = 10

const ALL_ROLES = [
  { value: '', label: 'Role: All' },
  { value: 'CUSTOMER', label: 'Role: Customer' },
  { value: 'MERCHANT', label: 'Role: Merchant' },
  { value: 'COURIER', label: 'Role: Courier' },
  { value: 'DISPATCHER', label: 'Role: Dispatcher' },
  { value: 'ADMIN', label: 'Role: Admin' },
]

const ALL_STATUSES = [
  { value: '', label: 'Status: All' },
  { value: 'ACTIVE', label: 'Status: Active' },
  { value: 'PENDING_APPROVAL', label: 'Status: Pending' },
  { value: 'SUSPENDED', label: 'Status: Suspended' },
  { value: 'DISABLED', label: 'Status: Disabled' },
]

const ASSIGNABLE_STATUSES = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'SUSPENDED', label: 'Suspended' },
  { value: 'DISABLED', label: 'Disabled' },
]

// Couriers and merchants apply themselves and are approved on their approval
// pages; customers register themselves. Only these roles are created here.
const STAFF_ROLE_OPTIONS = [
  {
    value: 'DISPATCHER',
    label: 'Dispatcher',
    description: 'Assigns parcels to couriers and monitors ongoing deliveries.',
  },
  {
    value: 'ADMIN',
    label: 'Administrator',
    description: 'Full access to users, approvals, zones, pricing and dispatch.',
  },
]

const EMPTY_STAFF_FORM = { role: 'DISPATCHER', name: '', email: '', password: '', confirmAdmin: false }

// ─── helpers ─────────────────────────────────────────────────────────────────

function roleBadgeClass(role) {
  switch (role) {
    case 'ADMIN': return 'badge badge-error'
    case 'DISPATCHER': return 'badge badge-info'
    case 'MERCHANT': return 'badge badge-warning'
    case 'COURIER': return 'badge badge-muted'
    default: return 'badge badge-success'   // CUSTOMER
  }
}

function statusBadgeClass(status) {
  switch (status) {
    case 'ACTIVE': return 'badge badge-success'
    case 'PENDING_APPROVAL': return 'badge badge-warning'
    case 'SUSPENDED': return 'badge badge-error'
    default: return 'badge badge-muted'  // DISABLED
  }
}

function statusLabel(status) {
  if (status === 'PENDING_APPROVAL') return 'Pending'
  if (!status) return '—'
  return status.charAt(0) + status.slice(1).toLowerCase()
}

function roleLabel(role) {
  if (!role) return '—'
  return role.charAt(0) + role.slice(1).toLowerCase()
}

function formatDate(value) {
  if (!value) return '—'
  const d = new Date(value)
  return Number.isNaN(d.getTime())
    ? '—'
    : d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
}

// ─── modals ──────────────────────────────────────────────────────────────────

function validateStaffAccount(values) {
  const errors = {}
  if (!values.name.trim()) errors.name = 'Enter the full name.'
  else if (values.name.trim().length > 100) errors.name = 'Name must be 100 characters or fewer.'
  if (!/^\S+@\S+\.\S+$/.test(values.email.trim())) errors.email = 'Enter a valid email address.'
  if (values.password.length < 12 || values.password.length > 72) {
    errors.password = 'Temporary password must be 12 to 72 characters.'
  }
  if (values.role === 'ADMIN' && !values.confirmAdmin) {
    errors.confirmAdmin = 'Confirm that this person should have full administrator access.'
  }
  return errors
}

function StaffField({ id, label, error, hint, children }) {
  const describedBy = [hint && `${id}-hint`, error && `${id}-error`].filter(Boolean).join(' ') || undefined
  return (
    <div className="form-field">
      <label className="form-label" htmlFor={id}>{label}</label>
      {children({ 'aria-invalid': error ? 'true' : undefined, 'aria-describedby': describedBy })}
      {hint && !error && <p id={`${id}-hint`} className="form-hint">{hint}</p>}
      {error && <p id={`${id}-error`} className="form-error">{error}</p>}
    </div>
  )
}

function CreateStaffModal({ onClose, onCreated }) {
  const [values, setValues] = useState(EMPTY_STAFF_FORM)
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  const isAdmin = values.role === 'ADMIN'

  const change = ({ target }) => {
    const value = target.type === 'checkbox' ? target.checked : target.value
    setValues((current) => ({
      ...current,
      [target.name]: value,
      // Choosing a role again must be confirmed again.
      ...(target.name === 'role' ? { confirmAdmin: false } : {}),
    }))
    setErrors((current) => ({ ...current, [target.name]: undefined, confirmAdmin: undefined }))
    setError(null)
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (saving) return
    const found = validateStaffAccount(values)
    setErrors(found)
    if (Object.keys(found).length > 0) return
    setSaving(true)
    setError(null)
    try {
      const created = await createStaffAccount(values)
      onCreated(created, `${roleLabel(created.role)} account created for ${created.name} (${created.email}).`)
    } catch (err) {
      setError(describeError(err, {
        byStatus: {
          400: 'Check the account details and temporary password.',
          403: 'Your account is not allowed to create staff accounts.',
          409: 'An account already uses that email address.',
        },
      }))
      setSaving(false)
    }
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="create-staff-title">
      <div className="modal-dialog aum-create-dialog">
        <div className="modal-header">
          <div>
            <h2 id="create-staff-title" className="modal-title">Create Staff Account</h2>
            <p className="modal-subtitle">The account is active immediately.</p>
          </div>
          <button type="button" className="modal-close-btn" onClick={onClose} aria-label="Close dialog">✕</button>
        </div>
        <form onSubmit={handleSubmit} noValidate>
          <div className="modal-body aum-create-body">
            {error && <div className="alert alert-error" role="alert">{error}</div>}

            <fieldset className="aum-role-fieldset">
              <legend className="form-label">Role</legend>
              <div className="aum-role-options">
                {STAFF_ROLE_OPTIONS.map((option) => (
                  <label
                    key={option.value}
                    className={`aum-role-option${values.role === option.value ? ' is-selected' : ''}`}
                  >
                    <input
                      type="radio"
                      name="role"
                      value={option.value}
                      checked={values.role === option.value}
                      onChange={change}
                    />
                    <span className="aum-role-option-text">
                      <span className="aum-role-option-title">{option.label}</span>
                      <span className="aum-role-option-desc">{option.description}</span>
                    </span>
                  </label>
                ))}
              </div>
            </fieldset>

            <StaffField id="staff-name" label="Full name" error={errors.name}>
              {(aria) => (
                <input id="staff-name" name="name" className="input" autoComplete="off"
                  value={values.name} onChange={change} {...aria} />
              )}
            </StaffField>

            <StaffField id="staff-email" label="Email" error={errors.email}>
              {(aria) => (
                <input id="staff-email" name="email" type="email" className="input" autoComplete="off"
                  value={values.email} onChange={change} {...aria} />
              )}
            </StaffField>

            <StaffField id="staff-password" label="Temporary password" error={errors.password}
              hint="12 to 72 characters. Share it through a secure channel; it is sent only to Identity Service.">
              {(aria) => (
                <Input id="staff-password" name="password" type="password" revealable
                  autoComplete="new-password" value={values.password} onChange={change}
                  invalid={Boolean(errors.password)} aria-describedby={aria['aria-describedby']} />
              )}
            </StaffField>

            {isAdmin ? (
              <div className="modal-notice-box aum-admin-warning">
                <strong>Administrators have full access</strong>
                They can manage every account, approve merchants and couriers,
                and create more administrators.
                <label className="aum-confirm-row" htmlFor="staff-confirm-admin">
                  <input id="staff-confirm-admin" name="confirmAdmin" type="checkbox"
                    checked={values.confirmAdmin} onChange={change}
                    aria-invalid={errors.confirmAdmin ? 'true' : undefined}
                    aria-describedby={errors.confirmAdmin ? 'staff-confirm-admin-error' : undefined} />
                  <span>I understand this person will have full administrator access.</span>
                </label>
                {errors.confirmAdmin && (
                  <p id="staff-confirm-admin-error" className="form-error">{errors.confirmAdmin}</p>
                )}
              </div>
            ) : (
              <div className="modal-notice-box aum-info-notice">
                Couriers and merchants apply themselves and are approved on the Courier and
                Merchant Approvals pages.
              </div>
            )}
          </div>
          <div className="modal-footer">
            <Button variant="secondary" type="button" onClick={onClose}>Cancel</Button>
            <Button type="submit" loading={saving} loadingText="Creating…" disabled={saving}>
              {isAdmin ? 'Create Administrator' : 'Create Dispatcher'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  )
}

function ChangeStatusModal({ user, onClose, onSuccess }) {
  const [status, setStatus] = useState(
    user.status === 'PENDING_APPROVAL' ? 'ACTIVE' : user.status
  )
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  const handleSubmit = async (e) => {
    e.preventDefault()
    setSaving(true)
    setError(null)
    try {
      const updated = await changeUserStatus(user.id, status)
      onSuccess(updated, `Status changed to ${statusLabel(status)} for ${user.name}.`)
    } catch (err) {
      setError(describeError(err, {
        byStatus: {
          403: 'You cannot change your own status or you lack permission.',
          400: 'This status change is not allowed for this account type.',
        },
      }))
      setSaving(false)
    }
  }

  const dangerStatuses = new Set(['SUSPENDED', 'DISABLED'])

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="change-status-title">
      <div className="modal-dialog">
        <div className="modal-header">
          <div>
            <h2 id="change-status-title" className="modal-title">Change Account Status</h2>
            <p className="modal-subtitle">{user.name} · {user.email}</p>
          </div>
          <button type="button" className="modal-close-btn" onClick={onClose} aria-label="Close dialog">✕</button>
        </div>
        <form onSubmit={handleSubmit}>
          <div className="modal-body">
            {error && <div className="alert alert-error" role="alert" style={{ marginBottom: 12 }}>{error}</div>}
            <div className="modal-notice-box" style={{ backgroundColor: '#fffbeb', borderColor: '#fde68a', color: '#92400e', marginBottom: 16 }}>
              Changing account status invalidates all active sessions for this user.
            </div>
            <div className="form-field">
              <label className="form-label" htmlFor="new-status">New Status</label>
              <select
                id="new-status"
                className="filter-select"
                style={{ width: '100%', fontSize: 14 }}
                value={status}
                onChange={(e) => setStatus(e.target.value)}
              >
                {ASSIGNABLE_STATUSES.map((s) => (
                  <option key={s.value} value={s.value}>{s.label}</option>
                ))}
              </select>
            </div>
            {dangerStatuses.has(status) && (
              <p style={{ margin: '8px 0 0', fontSize: 13, color: '#dc2626' }}>
                {status === 'DISABLED'
                  ? 'Disabled accounts cannot sign in and cannot be re-enabled from this panel.'
                  : 'Suspended accounts cannot sign in until reactivated.'}
              </p>
            )}
          </div>
          <div className="modal-footer">
            <Button variant="secondary" type="button" onClick={onClose}>Cancel</Button>
            <Button
              type="submit"
              loading={saving}
              loadingText="Saving…"
              disabled={saving}
              style={dangerStatuses.has(status) ? { backgroundColor: '#dc2626', borderColor: '#dc2626' } : {}}
            >
              Confirm Change
            </Button>
          </div>
        </form>
      </div>
    </div>
  )
}

function UserDetailModal({ user, onClose, onChangeStatus }) {
  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="user-detail-title">
      <div className="modal-dialog" style={{ maxWidth: 520 }}>
        <div className="modal-header">
          <div>
            <h2 id="user-detail-title" className="modal-title">{user.name}</h2>
            <p className="modal-subtitle">{user.email}</p>
          </div>
          <button type="button" className="modal-close-btn" onClick={onClose} aria-label="Close dialog">✕</button>
        </div>
        <div className="modal-body">
          <dl className="aum-detail-grid">
            <div className="aum-detail-row">
              <dt className="aum-detail-label">Role</dt>
              <dd><span className={roleBadgeClass(user.role)}>{roleLabel(user.role)}</span></dd>
            </div>
            <div className="aum-detail-row">
              <dt className="aum-detail-label">Status</dt>
              <dd><span className={statusBadgeClass(user.status)}>{statusLabel(user.status)}</span></dd>
            </div>
            <div className="aum-detail-row">
              <dt className="aum-detail-label">Phone</dt>
              <dd className="aum-detail-value">{user.phone || '—'}</dd>
            </div>
            <div className="aum-detail-row">
              <dt className="aum-detail-label">Address</dt>
              <dd className="aum-detail-value">{user.address || '—'}</dd>
            </div>
            <div className="aum-detail-row">
              <dt className="aum-detail-label">Member Since</dt>
              <dd className="aum-detail-value">{formatDate(user.createdAt)}</dd>
            </div>
            <div className="aum-detail-row">
              <dt className="aum-detail-label">User ID</dt>
              <dd className="aum-detail-value" style={{ fontFamily: 'monospace', fontSize: 12 }}>#{user.id}</dd>
            </div>
          </dl>
        </div>
        <div className="modal-footer">
          <Button variant="secondary" onClick={onClose}>Close</Button>
          <Button onClick={() => { onClose(); onChangeStatus(user) }}>Change Status</Button>
        </div>
      </div>
    </div>
  )
}

// ─── main page ───────────────────────────────────────────────────────────────

export default function AdminUserManagementPage() {
  const { user: adminUser } = useAuth()

  // Filters
  const [search, setSearch] = useState('')
  const [roleFilter, setRoleFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [page, setPage] = useState(0)   // 0-based, matches Spring Pageable

  // Data
  const [users, setUsers] = useState([])
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  // Stats
  const [stats, setStats] = useState({ pending: 0, active: 0, restricted: 0 })

  // Feedback notifications
  const [actionSuccess, setActionSuccess] = useState('')
  const [actionError, setActionError] = useState(null)

  // Modals
  const [detailUser, setDetailUser] = useState(null)
  const [statusModalUser, setStatusModalUser] = useState(null)
  const [createOpen, setCreateOpen] = useState(false)

  // Search debounce
  const searchTimer = useRef(null)
  const [debouncedSearch, setDebouncedSearch] = useState('')

  const handleSearchChange = (e) => {
    setSearch(e.target.value)
    clearTimeout(searchTimer.current)
    searchTimer.current = setTimeout(() => {
      setDebouncedSearch(e.target.value)
      setPage(0)
    }, 350)
  }

  // Fetch summary stats
  const fetchStats = useCallback(async (signal) => {
    try {
      const [pendingRes, activeRes, suspendedRes, disabledRes] = await Promise.all([
        listUsers({ status: 'PENDING_APPROVAL', size: 1 }, signal),
        listUsers({ status: 'ACTIVE', size: 1 }, signal),
        listUsers({ status: 'SUSPENDED', size: 1 }, signal),
        listUsers({ status: 'DISABLED', size: 1 }, signal),
      ])
      setStats({
        pending: pendingRes?.totalElements ?? 0,
        active: activeRes?.totalElements ?? 0,
        restricted: (suspendedRes?.totalElements ?? 0) + (disabledRes?.totalElements ?? 0),
      })
    } catch {
      // Ignore stats fetch failure
    }
  }, [])

  // Fetch users whenever filters or page change
  const fetchUsers = useCallback((signal) => {
    setLoading(true)
    setError(null)
    return listUsers(
      { search: debouncedSearch, role: roleFilter, status: statusFilter, page, size: PAGE_SIZE },
      signal
    )
      .then((data) => {
        const content = Array.isArray(data) ? data : (data?.content ?? [])
        setUsers(content)
        setTotalPages(data?.totalPages ?? 1)
        setTotalElements(data?.totalElements ?? content.length)
        setLoading(false)
      })
      .catch((err) => {
        if (err?.name !== 'AbortError') {
          setError(err)
          setLoading(false)
        }
      })
  }, [debouncedSearch, roleFilter, statusFilter, page])

  useEffect(() => {
    const controller = new AbortController()
    fetchUsers(controller.signal)
    fetchStats(controller.signal)
    return () => controller.abort()
  }, [fetchUsers, fetchStats])

  // Auto-clear success message after 4s
  useEffect(() => {
    if (!actionSuccess) return
    const t = setTimeout(() => setActionSuccess(''), 4000)
    return () => clearTimeout(t)
  }, [actionSuccess])

  const handleFilterChange = (setter) => (e) => {
    setter(e.target.value)
    setPage(0)
  }

  const handleResetFilters = () => {
    setSearch('')
    setDebouncedSearch('')
    setRoleFilter('')
    setStatusFilter('')
    setPage(0)
    clearTimeout(searchTimer.current)
  }

  const handleActionSuccess = (updatedUser, msg) => {
    setUsers((prev) => prev.map((u) => (u.id === updatedUser.id ? updatedUser : u)))
    setActionSuccess(msg)
    setActionError(null)
    setStatusModalUser(null)
    fetchStats()
  }

  const handleStaffCreated = (_created, msg) => {
    setCreateOpen(false)
    setActionSuccess(msg)
    setActionError(null)
    // Newest accounts are listed first, so show the first page to reveal the new one.
    if (page === 0) fetchUsers()
    else setPage(0)
    fetchStats()
  }

  const pageNumbers = () => {
    const total = totalPages
    if (total <= 7) return Array.from({ length: total }, (_, i) => i)
    const delta = 2
    const left = Math.max(0, page - delta)
    const right = Math.min(total - 1, page + delta)
    const pages = []
    if (left > 0) { pages.push(0); if (left > 1) pages.push('…') }
    for (let i = left; i <= right; i++) pages.push(i)
    if (right < total - 1) { if (right < total - 2) pages.push('…'); pages.push(total - 1) }
    return pages
  }

  return (
    <div className="merchant-scope admin-approvals">
      {/* Breadcrumb */}
      <nav className="page-breadcrumb" aria-label="Breadcrumb">
        <span>Admin</span>
        <span>/</span>
        <strong>User Management</strong>
      </nav>

      {/* Header */}
      <header className="page-header-row">
        <div>
          <h1 className="page-title">User Accounts</h1>
          <p className="page-subtitle">Search, review and manage every SwiftDrop account and its access.</p>
        </div>
        <Button type="button" onClick={() => setCreateOpen(true)} className="aum-create-btn">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor"
            strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <line x1="12" y1="5" x2="12" y2="19" />
            <line x1="5" y1="12" x2="19" y2="12" />
          </svg>
          Create Staff Account
        </Button>
      </header>

      {/* Global Notifications */}
      {actionSuccess && (
        <div className="alert alert-success" role="status" style={{ marginBottom: '20px' }}>
          <span>✓ {actionSuccess}</span>
          <button
            type="button"
            className="alert-dismiss"
            onClick={() => setActionSuccess('')}
            aria-label="Dismiss notification"
          >
            Dismiss
          </button>
        </div>
      )}

      {actionError && (
        <div className="alert alert-error" role="alert" style={{ marginBottom: '20px' }}>
          <span>{actionError}</span>
          <button
            type="button"
            className="alert-dismiss"
            onClick={() => setActionError(null)}
            aria-label="Dismiss error"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* 3 Stat Cards */}
      <section aria-label="Account statistics">
        <div className="stats-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))' }}>
          <div className="stat-card stat-card-amber">
            <span className="stat-label">Pending Approval</span>
            <span className="stat-value">{stats.pending}</span>
            <span className="stat-subtext">Awaiting activation</span>
          </div>

          <div className="stat-card stat-card-green">
            <span className="stat-label">Active Users</span>
            <span className="stat-value">{stats.active}</span>
            <span className="stat-subtext">Usable accounts</span>
          </div>

          <div className="stat-card stat-card-red">
            <span className="stat-label">Suspended / Disabled</span>
            <span className="stat-value">{stats.restricted}</span>
            <span className="stat-subtext">Restricted access</span>
          </div>
        </div>
      </section>

      {/* Main Table Card */}
      <section className="card-table" aria-label="Users list">
        <div className="card-table-header">
          <div className="card-table-title">
            All users <span className="card-table-subtitle">{totalElements} total</span>
          </div>
        </div>

        {/* Filter Bar */}
        <div className="filter-bar">
          <div className="search-input-wrapper">
            <span className="search-icon" aria-hidden="true">🔍</span>
            <input
              type="search"
              className="input"
              placeholder="Search name or email"
              value={search}
              onChange={handleSearchChange}
              aria-label="Search users"
            />
          </div>

          <select
            className="filter-select"
            value={roleFilter}
            onChange={handleFilterChange(setRoleFilter)}
            aria-label="Filter by role"
          >
            {ALL_ROLES.map((r) => (
              <option key={r.value} value={r.value}>{r.label}</option>
            ))}
          </select>

          <select
            className="filter-select"
            value={statusFilter}
            onChange={handleFilterChange(setStatusFilter)}
            aria-label="Filter by status"
          >
            {ALL_STATUSES.map((s) => (
              <option key={s.value} value={s.value}>{s.label}</option>
            ))}
          </select>

          <button
            type="button"
            className="action-link action-link-primary"
            onClick={handleResetFilters}
            style={{ marginLeft: '4px' }}
          >
            Reset
          </button>
        </div>

        {/* Loading state */}
        {loading && (
          <div className="card status-card status-card-loading" style={{ margin: '30px auto', border: 'none', boxShadow: 'none' }}>
            <LoadingSpinner />
            <p role="status">Loading users…</p>
          </div>
        )}

        {/* Error state */}
        {!loading && error && (
          <div className="card status-card" role="alert" style={{ margin: '30px auto', border: 'none', boxShadow: 'none' }}>
            <h2 className="card-title">Unable to load users</h2>
            <p className="card-subtitle">{describeError(error, { byStatus: { 403: 'You do not have permission to view users.' } })}</p>
            <Button onClick={() => fetchUsers()}>Try again</Button>
          </div>
        )}

        {/* Empty state */}
        {!loading && !error && users.length === 0 && (
          <div style={{ textAlign: 'center', padding: '48px 24px', color: 'var(--color-text-muted)' }}>
            <p style={{ margin: 0, fontSize: 14 }}>
              {debouncedSearch || roleFilter || statusFilter
                ? 'No users match your current filters.'
                : 'No users found.'}
            </p>
            {(debouncedSearch || roleFilter || statusFilter) && (
              <button
                type="button"
                className="action-link action-link-primary"
                onClick={handleResetFilters}
                style={{ marginTop: 8 }}
              >
                Clear filters
              </button>
            )}
          </div>
        )}

        {/* Users Data Table */}
        {!loading && !error && users.length > 0 && (
          <div className="table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th scope="col">Name</th>
                  <th scope="col">Email</th>
                  <th scope="col">Phone</th>
                  <th scope="col">Role</th>
                  <th scope="col">Status</th>
                  <th scope="col">Joined Date</th>
                  <th scope="col" style={{ width: 160 }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {users.map((u) => (
                  <tr key={u.id}>
                    <td>
                      <strong>{u.name}</strong>
                      {adminUser?.id === u.id && (
                        <span className="badge badge-info" style={{ marginLeft: 6, fontSize: 10 }}>
                          You
                        </span>
                      )}
                    </td>
                    <td>{u.email}</td>
                    <td>{u.phone || '—'}</td>
                    <td><span className={roleBadgeClass(u.role)}>{roleLabel(u.role)}</span></td>
                    <td><span className={statusBadgeClass(u.status)}>{statusLabel(u.status)}</span></td>
                    <td>{formatDate(u.createdAt)}</td>
                    <td>
                      <div className="aum-actions">
                        <button
                          type="button"
                          className="aum-action-btn"
                          onClick={() => setDetailUser(u)}
                          title="View details"
                        >
                          View
                        </button>
                        <button
                          type="button"
                          className="aum-action-btn aum-action-btn-danger"
                          onClick={() => setStatusModalUser(u)}
                          disabled={adminUser?.id === u.id}
                          title={adminUser?.id === u.id ? 'Cannot change your own status' : 'Change status'}
                        >
                          Status
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Footer & Pagination */}
        {!loading && !error && totalElements > 0 && (
          <div className="card-table-footer">
            <span>
              Showing {totalElements === 0 ? 0 : page * PAGE_SIZE + 1}–
              {Math.min((page + 1) * PAGE_SIZE, totalElements)} of {totalElements} users
            </span>

            {totalPages > 1 && (
              <div className="pagination" role="navigation" aria-label="Pagination">
                <button
                  type="button"
                  className="pagination-btn"
                  disabled={page <= 0}
                  onClick={() => setPage((p) => p - 1)}
                  aria-label="Previous page"
                >
                  ‹ Previous
                </button>

                {pageNumbers().map((pageNum, idx) =>
                  pageNum === '…' ? (
                    <span key={`ellipsis-${idx}`} className="pagination-btn" style={{ cursor: 'default', color: 'var(--color-text-muted)' }}>
                      …
                    </span>
                  ) : (
                    <button
                      key={pageNum}
                      type="button"
                      className={`pagination-btn ${pageNum === page ? 'active' : ''}`}
                      onClick={() => setPage(pageNum)}
                      aria-current={pageNum === page ? 'page' : undefined}
                    >
                      {pageNum + 1}
                    </button>
                  )
                )}

                <button
                  type="button"
                  className="pagination-btn"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((p) => p + 1)}
                  aria-label="Next page"
                >
                  Next ›
                </button>
              </div>
            )}
          </div>
        )}
      </section>

      {/* Modals */}
      {createOpen && (
        <CreateStaffModal onClose={() => setCreateOpen(false)} onCreated={handleStaffCreated} />
      )}
      {detailUser && (
        <UserDetailModal
          user={detailUser}
          onClose={() => setDetailUser(null)}
          onChangeStatus={(u) => setStatusModalUser(u)}
        />
      )}
      {statusModalUser && (
        <ChangeStatusModal
          user={statusModalUser}
          onClose={() => setStatusModalUser(null)}
          onSuccess={handleActionSuccess}
        />
      )}
    </div>
  )
}
