import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import Alert from '../../components/Alert'
import Button from '../../components/Button'
import Checkbox from '../../components/Checkbox'
import FormField from '../../components/FormField'
import LoadingSpinner from '../../components/LoadingSpinner'
import { useAuth } from '../../context/AuthContext'
import { createStaffAccount } from '../../services/adminUserService'
import { dispatchService } from '../../services/dispatchService'
import { describeError } from '../../services/errorMessages'
import { merchantService } from '../../services/merchantService'
import { parcelService } from '../../services/parcelService'

// ─── Helpers ─────────────────────────────────────────────────────────────────

const ROLE_OPTIONS = [
  { value: 'DISPATCHER', label: 'Dispatcher' },
  { value: 'ADMIN', label: 'Administrator' },
]

const ROLE_NAMES = { DISPATCHER: 'Dispatcher', ADMIN: 'Administrator' }

const EMPTY_FORM = { name: '', email: '', password: '', role: 'DISPATCHER', confirmAdmin: false }

const IN_PROGRESS_STATUSES = new Set(['ASSIGNED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY'])

function validate(values) {
  const errors = {}
  if (!values.name.trim()) errors.name = 'Enter the staff member’s full name.'
  if (!/^\S+@\S+\.\S+$/.test(values.email.trim())) errors.email = 'Enter a valid email address.'
  if (values.password.length < 12 || values.password.length > 72) {
    errors.password = 'Temporary password must be 12 to 72 characters.'
  }
  if (!ROLE_NAMES[values.role]) errors.role = 'Choose Dispatcher or Administrator.'
  if (values.role === 'ADMIN' && !values.confirmAdmin) {
    errors.confirmAdmin = 'Confirm that this person should have full administrator access.'
  }
  return errors
}

/** Sri Lankan date string YYYY-MM-DD */
function getSriLankaDateStr(date = new Date()) {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Colombo' }).format(date)
}

/** Check if parcel createdAt falls in the current Sri Lankan calendar day */
function isCreatedToday(isoString) {
  if (!isoString) return false
  const date = new Date(isoString)
  if (Number.isNaN(date.getTime())) return false
  return getSriLankaDateStr(date) === getSriLankaDateStr()
}

/** 7-day range for Sri Lanka calendar days: from 6 days ago through today */
function get7DayRange() {
  const now = new Date()
  const to = getSriLankaDateStr(now)
  const fromDate = new Date(now.getTime() - 6 * 24 * 60 * 60 * 1000)
  const from = getSriLankaDateStr(fromDate)
  return { from, to }
}

/** Format YYYY-MM-DD to "D MMM" (e.g. "13 Nov") */
function formatDayLabel(dateStr) {
  if (!dateStr) return ''
  const d = new Date(`${dateStr}T00:00:00`)
  return new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short' }).format(d)
}

// ─── Icons ───────────────────────────────────────────────────────────────────

function PackageIcon({ size = 20 }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M16.5 9.4 7.55 4.24a1.78 1.78 0 0 0-2.5 1.55v12.42a1.78 1.78 0 0 0 2.5 1.55L16.5 14.6" />
      <polyline points="3.29 7 12 12 20.71 7" />
      <line x1="12" y1="22" x2="12" y2="12" />
      <path d="m21.5 14.6-8.95 5.16a1.78 1.78 0 0 1-2.5-1.55V5.79a1.78 1.78 0 0 1 2.5-1.55L21.5 9.4a1.78 1.78 0 0 1 0 3.2z" />
    </svg>
  )
}

function TruckIcon({ size = 20 }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="1" y="3" width="15" height="13" />
      <polygon points="16 8 20 8 23 11 23 16 16 16 16 8" />
      <circle cx="5.5" cy="18.5" r="2.5" />
      <circle cx="18.5" cy="18.5" r="2.5" />
    </svg>
  )
}

function StoreIcon({ size = 20 }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="m2 7 4.41-4.41A2 2 0 0 1 7.83 2h8.34a2 2 0 0 1 1.42.59L22 7" />
      <path d="M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8" />
      <path d="M15 22v-4a2 2 0 0 0-2-2h-2a2 2 0 0 0-2 2v4" />
      <path d="M2 7h20" />
    </svg>
  )
}

function XCircleIcon({ size = 20 }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="12" cy="12" r="10" />
      <line x1="15" y1="9" x2="9" y2="15" />
      <line x1="9" y1="9" x2="15" y2="15" />
    </svg>
  )
}

function WarningTriangleIcon({ size = 20 }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z" />
      <line x1="12" y1="9" x2="12" y2="13" />
      <line x1="12" y1="17" x2="12.01" y2="17" />
    </svg>
  )
}

// ─── Stacked Bar Chart Component ──────────────────────────────────────────────

function DeliveriesBarChart({ days }) {
  if (!days || days.length === 0) {
    return <p className="admin-chart-empty">No delivery activity recorded for the last 7 days.</p>
  }

  const maxTotal = Math.max(1, ...days.map((d) => d.total || 0))

  return (
    <div className="admin-bar-chart-container">
      <div className="admin-bar-chart-plot" role="img" aria-label="Deliveries per day last 7 days">
        {days.map((day) => {
          const total = day.total || 0
          const delivered = day.delivered || 0
          const failed = day.failed || 0

          // Calculate height relative to max in plot (max 160px height)
          const barHeightPct = total > 0 ? Math.max((total / maxTotal) * 100, 6) : 0
          const deliveredPct = total > 0 ? (delivered / total) * 100 : 0
          const failedPct = total > 0 ? (failed / total) * 100 : 0

          return (
            <div className="admin-bar-col" key={day.date}>
              <div className="admin-bar-track">
                {total > 0 && <span className="admin-bar-value">{total}</span>}
                {total > 0 ? (
                  <div className="admin-bar-stack" style={{ height: `${barHeightPct}%` }}>
                    {failed > 0 && (
                      <div
                        className="admin-bar-segment admin-segment-failed"
                        style={{ height: `${failedPct}%` }}
                        title={`${failed} failed on ${formatDayLabel(day.date)}`}
                      />
                    )}
                    {delivered > 0 && (
                      <div
                        className="admin-bar-segment admin-segment-delivered"
                        style={{ height: `${deliveredPct}%` }}
                        title={`${delivered} delivered on ${formatDayLabel(day.date)}`}
                      />
                    )}
                  </div>
                ) : (
                  <div className="admin-bar-empty-placeholder" />
                )}
              </div>
              <span className="admin-bar-label">{formatDayLabel(day.date)}</span>
            </div>
          )
        })}
      </div>

      <div className="admin-chart-legend">
        <span className="admin-legend-item">
          <span className="admin-legend-dot admin-dot-delivered" />
          <span>Delivered</span>
        </span>
        <span className="admin-legend-item">
          <span className="admin-legend-dot admin-dot-failed" />
          <span>Failed</span>
        </span>
      </div>
    </div>
  )
}

// ─── Main Admin Dashboard Component ──────────────────────────────────────────

export default function AdminDashboardPage() {
  const { user } = useAuth()

  // Overview metrics state
  const [stats, setStats] = useState(null)
  const [statsLoading, setStatsLoading] = useState(true)
  const [statsError, setStatsError] = useState(null)

  // Staff account creation form state
  const [values, setValues] = useState(EMPTY_FORM)
  const [errors, setErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)

  const isAdmin = values.role === 'ADMIN'
  const roleName = ROLE_NAMES[values.role] ?? 'Staff'

  const loadDashboardData = useCallback(() => {
    const controller = new AbortController()
    setStatsLoading(true)
    setStatsError(null)

    const { from, to } = get7DayRange()

    Promise.all([
      dispatchService.getSummary({ from, to }, controller.signal).catch((err) => {
        if (err?.name === 'AbortError') throw err
        return null
      }),
      dispatchService.getCouriers(controller.signal).catch((err) => {
        if (err?.name === 'AbortError') throw err
        return []
      }),
      merchantService.getAllMerchants('PENDING', controller.signal).catch((err) => {
        if (err?.name === 'AbortError') throw err
        return []
      }),
      parcelService.listForDispatch(undefined, controller.signal).catch((err) => {
        if (err?.name === 'AbortError') throw err
        return []
      }),
    ])
      .then(([summary, couriers, pendingMerchants, parcels]) => {
        if (controller.signal.aborted) return

        const couriersList = Array.isArray(couriers) ? couriers : []
        const merchantsList = Array.isArray(pendingMerchants) ? pendingMerchants : []
        const parcelsList = Array.isArray(parcels) ? parcels : []

        // Deliveries created today
        const deliveriesToday = parcelsList.filter((p) => isCreatedToday(p.createdAt)).length

        // Active couriers
        const activeCouriers = couriersList.filter((c) => Boolean(c.active)).length
        const totalCouriers = couriersList.length

        // Pending merchant approvals
        const pendingApprovalsCount = merchantsList.length

        // 7-day summary metrics
        const deliveriesPerDay = summary?.deliveriesPerDay || []
        const deliveredCount = deliveriesPerDay.reduce((sum, d) => sum + (d.delivered || 0), 0)
        const failedCount = deliveriesPerDay.reduce((sum, d) => sum + (d.failed || 0), 0)
        const successRate = summary?.successRate != null ? Number(summary.successRate).toFixed(1) : '0.0'

        // In-progress deliveries
        const inProgressCount = parcelsList.filter((p) => IN_PROGRESS_STATUSES.has(p.status)).length

        setStats({
          deliveriesToday,
          activeCouriers,
          totalCouriers,
          pendingApprovalsCount,
          pendingMerchants: merchantsList,
          deliveriesPerDay,
          deliveredCount,
          failedCount,
          successRate,
          inProgressCount,
        })
        setStatsLoading(false)
      })
      .catch((err) => {
        if (controller.signal.aborted || err?.name === 'AbortError') return
        setStatsError(err.message || 'Unable to load dashboard overview data.')
        setStatsLoading(false)
      })

    return () => controller.abort()
  }, [])

  useEffect(() => {
    const cancel = loadDashboardData()
    return cancel
  }, [loadDashboardData])

  // Form input change handler
  const change = ({ target }) => {
    const value = target.type === 'checkbox' ? target.checked : target.value
    setValues((current) => ({
      ...current,
      [target.name]: value,
      // A changed role must be confirmed again.
      ...(target.name === 'role' ? { confirmAdmin: false } : {}),
    }))
    setErrors((current) => ({ ...current, [target.name]: undefined, confirmAdmin: undefined }))
    setResult(null)
    setError(null)
  }

  // Create staff account submit handler
  const submit = async (event) => {
    event.preventDefault()
    const found = validate(values)
    setErrors(found)
    if (Object.keys(found).length > 0 || submitting) return
    setSubmitting(true)
    setError(null)
    try {
      const created = await createStaffAccount(values)
      setResult(created)
      setValues(EMPTY_FORM)
    } catch (caught) {
      setError(describeError(caught, {
        byStatus: {
          400: 'Check the account details and temporary password.',
          403: 'Your account is not allowed to create staff users.',
          409: 'An account already uses that email address.',
        },
      }))
    } finally {
      setSubmitting(false)
    }
  }

  // Pending merchant names list
  const pendingMerchantNames = stats?.pendingMerchants
    ?.map((m) => m.businessName || m.contactName)
    ?.filter(Boolean) || []

  // Success bar percentages
  const totalFinished = (stats?.deliveredCount || 0) + (stats?.failedCount || 0)
  const deliveredPct = totalFinished > 0 ? ((stats?.deliveredCount || 0) / totalFinished) * 100 : 0
  const failedPct = totalFinished > 0 ? ((stats?.failedCount || 0) / totalFinished) * 100 : 0

  return (
    <div className="admin-overview-container">
      {/* ── Header ── */}
      <header className="admin-overview-header">
        <h1 className="admin-overview-title">Admin Overview</h1>
        <p className="admin-overview-subtitle">
          {user?.name ? `Welcome, ${user.name}. ` : ''}Real-time metrics calculated from live operational records.
        </p>
      </header>

      {/* ── Loading / Error States ── */}
      {statsLoading ? (
        <div className="card admin-loading-card">
          <LoadingSpinner />
          <p role="status">Loading dashboard metrics…</p>
        </div>
      ) : statsError ? (
        <div className="card admin-error-card" role="alert">
          <p className="admin-error-text">{statsError}</p>
          <Button variant="secondary" onClick={loadDashboardData}>
            Retry
          </Button>
        </div>
      ) : stats ? (
        <>
          {/* ── A. Overview Statistics Cards ── */}
          <section className="admin-kpi-grid" aria-label="Key operational statistics">
            {/* Deliveries Today */}
            <article className="admin-kpi-card card">
              <div className="admin-kpi-top">
                <span className="admin-kpi-label">Deliveries today</span>
                <span className="admin-kpi-icon" aria-hidden="true">
                  <PackageIcon />
                </span>
              </div>
              <div className="admin-kpi-value">{stats.deliveriesToday}</div>
              <div className="admin-kpi-sub">Bookings created today</div>
            </article>

            {/* Active Couriers */}
            <article className="admin-kpi-card card">
              <div className="admin-kpi-top">
                <span className="admin-kpi-label">Active couriers</span>
                <span className="admin-kpi-icon" aria-hidden="true">
                  <TruckIcon />
                </span>
              </div>
              <div className="admin-kpi-value">{stats.activeCouriers}</div>
              <div className="admin-kpi-sub">of {stats.totalCouriers} couriers</div>
            </article>

            {/* Pending Merchant Approvals */}
            <article className="admin-kpi-card card">
              <div className="admin-kpi-top">
                <span className="admin-kpi-label">Pending merchant approvals</span>
                <span className="admin-kpi-icon" aria-hidden="true">
                  <StoreIcon />
                </span>
              </div>
              <div className={`admin-kpi-value${stats.pendingApprovalsCount > 0 ? ' text-amber' : ''}`}>
                {stats.pendingApprovalsCount}
              </div>
              <div className="admin-kpi-sub">Awaiting admin review</div>
            </article>

            {/* Failed Deliveries */}
            <article className="admin-kpi-card card">
              <div className="admin-kpi-top">
                <span className="admin-kpi-label">Failed deliveries</span>
                <span className="admin-kpi-icon" aria-hidden="true">
                  <XCircleIcon />
                </span>
              </div>
              <div className={`admin-kpi-value${stats.failedCount > 0 ? ' text-red' : ''}`}>
                {stats.failedCount}
              </div>
              <div className="admin-kpi-sub">Currently in Failed status</div>
            </article>
          </section>

          {/* ── B & C. Deliveries Per Day Chart + Summary Card ── */}
          <div className="admin-charts-grid">
            {/* Left: Deliveries Per Day Stacked Bar Chart */}
            <section className="admin-chart-card card" aria-labelledby="chart-deliveries-title">
              <div className="admin-card-header">
                <h2 id="chart-deliveries-title" className="admin-card-title">Deliveries per day</h2>
                <p className="admin-card-desc">Last 7 days, by completion date</p>
              </div>
              <DeliveriesBarChart days={stats.deliveriesPerDay} />
            </section>

            {/* Right: Success / Failure Summary Card */}
            <section className="admin-chart-card card" aria-labelledby="summary-deliveries-title">
              <div className="admin-card-header">
                <h2 id="summary-deliveries-title" className="admin-card-title">Success / failure summary</h2>
              </div>
              <div className="admin-summary-content">
                <div className="admin-summary-list">
                  <div className="admin-summary-row">
                    <span className="admin-summary-label">Delivered</span>
                    <span className="admin-summary-val">{stats.deliveredCount}</span>
                  </div>
                  <div className="admin-summary-row">
                    <span className="admin-summary-label">Failed</span>
                    <span className="admin-summary-val">{stats.failedCount}</span>
                  </div>
                  <div className="admin-summary-row">
                    <span className="admin-summary-label">Success rate</span>
                    <span className="admin-summary-val">{stats.successRate}%</span>
                  </div>
                  <div className="admin-summary-row">
                    <span className="admin-summary-label">In progress</span>
                    <span className="admin-summary-val">{stats.inProgressCount}</span>
                  </div>
                </div>

                {/* Dual-color Progress Bar */}
                <div className="admin-progress-container" aria-hidden="true">
                  <div className="admin-progress-track">
                    {totalFinished > 0 ? (
                      <>
                        <div
                          className="admin-progress-delivered"
                          style={{ width: `${deliveredPct}%` }}
                        />
                        <div
                          className="admin-progress-failed"
                          style={{ width: `${failedPct}%` }}
                        />
                      </>
                    ) : (
                      <div className="admin-progress-empty" />
                    )}
                  </div>
                  <p className="admin-progress-caption">
                    Success rate = delivered ÷ (delivered + failed). In-progress parcels excluded.
                  </p>
                </div>
              </div>
            </section>
          </div>

          {/* ── D. Pending Merchant Approval Alert ── */}
          {stats.pendingApprovalsCount > 0 && (
            <section className="admin-alert-banner" aria-label="Pending approvals alert">
              <div className="admin-alert-body">
                <span className="admin-alert-icon" aria-hidden="true">
                  <WarningTriangleIcon size={20} />
                </span>
                <div className="admin-alert-info">
                  <strong className="admin-alert-title">
                    {stats.pendingApprovalsCount} merchant application(s) awaiting review
                  </strong>
                  {pendingMerchantNames.length > 0 && (
                    <p className="admin-alert-desc">
                      {pendingMerchantNames.slice(0, 3).join(', ')}
                      {pendingMerchantNames.length > 3 ? ` and ${pendingMerchantNames.length - 3} more` : ''}
                    </p>
                  )}
                </div>
              </div>
              <Link to="/admin/merchant-approvals" className="admin-alert-btn">
                Review
              </Link>
            </section>
          )}
        </>
      ) : null}
      {/* ── Staff Account Creation Card ── */}
      <section className="card admin-dispatcher-card" aria-labelledby="create-staff-title">
        <h2 id="create-staff-title" className="card-title">Create staff account</h2>
        <p className="card-subtitle">
          The account is active immediately. Share the temporary password through a secure channel.
          Couriers and merchants apply themselves and are approved on their approval pages.
        </p>

        {result && (
          <Alert variant="success">
            {ROLE_NAMES[result.role] ?? 'Staff'} {result.name} ({result.email}) was created successfully.
          </Alert>
        )}
        {error && <Alert variant="error">{error}</Alert>}

        <form className="form" onSubmit={submit} noValidate>
          <FormField
            id="staff-role"
            name="role"
            label="Role"
            as="select"
            required
            placeholder="Select a role"
            options={ROLE_OPTIONS}
            value={values.role}
            onChange={change}
            error={errors.role}
          />
          <FormField
            id="staff-name"
            name="name"
            label="Full name"
            required
            autoComplete="off"
            value={values.name}
            onChange={change}
            error={errors.name}
          />
          <FormField
            id="staff-email"
            name="email"
            label="Email"
            type="email"
            required
            autoComplete="off"
            value={values.email}
            onChange={change}
            error={errors.email}
          />
          <FormField
            id="staff-password"
            name="password"
            label="Temporary password"
            type="password"
            revealable
            required
            autoComplete="new-password"
            hint="12 to 72 characters. The password is sent only to Identity Service."
            value={values.password}
            onChange={change}
            error={errors.password}
          />

          {isAdmin && (
            <div className="form-field">
              <Alert variant="warning">
                An administrator has full access: they can manage every user, change roles,
                approve merchants and couriers, and create more administrators.
              </Alert>
              <Checkbox
                id="staff-confirm-admin"
                name="confirmAdmin"
                label="I understand this person will have full administrator access."
                checked={values.confirmAdmin}
                onChange={change}
                aria-invalid={errors.confirmAdmin ? 'true' : undefined}
                aria-describedby={errors.confirmAdmin ? 'staff-confirm-admin-error' : undefined}
              />
              {errors.confirmAdmin && (
                <p id="staff-confirm-admin-error" className="form-error">{errors.confirmAdmin}</p>
              )}
            </div>
          )}

          <Button type="submit" loading={submitting} loadingText={`Creating ${roleName.toLowerCase()}…`}>
            Create {roleName.toLowerCase()}
          </Button>
        </form>

        <p className="form-hint" style={{ marginTop: '16px' }}>
          Looking to view, search, or update accounts? Go to{' '}
          <Link
            to="/admin/users"
            style={{ color: 'var(--color-primary)', fontWeight: 500, textDecoration: 'underline' }}
          >
            User Management
          </Link>.
        </p>
      </section>
    </div>
  )
}
