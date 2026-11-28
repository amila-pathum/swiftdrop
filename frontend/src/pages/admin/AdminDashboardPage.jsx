import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import Button from '../../components/Button'
import LoadingSpinner from '../../components/LoadingSpinner'
import { useAuth } from '../../context/AuthContext'
import { dispatchService } from '../../services/dispatchService'
import { merchantService } from '../../services/merchantService'
import { parcelService } from '../../services/parcelService'
import { DashboardHeader, KpiCard, KpiGrid, Panel } from '../../components/dashboard/Dashboard'
import { todayLabel } from '../../utils/dashboardFormat'
import DashboardIcon from '../../components/dashboard/DashboardIcon'
import './adminDashboard.css'

const PIPELINE = [
  ['CREATED', 'Awaiting courier', '#94a3b8'],
  ['ASSIGNED', 'Assigned', '#a78bfa'],
  ['PICKED_UP', 'Picked up', '#38bdf8'],
  ['IN_TRANSIT', 'In transit', '#1c5dfb'],
  ['OUT_FOR_DELIVERY', 'Out for delivery', '#f59e0b'],
  ['DELIVERED', 'Delivered', '#16a34a'],
  ['FAILED', 'Failed', '#dc2626'],
  ['CANCELLED', 'Cancelled', '#cbd5e1'],
]
const AVAILABILITY = [['AVAILABLE', 'Available', 'success'], ['BUSY', 'Busy', 'warning'], ['OFFLINE', 'Offline', ''], ['INACTIVE', 'Inactive', 'danger']]

// ─── Helpers ─────────────────────────────────────────────────────────────────

const IN_PROGRESS_STATUSES = new Set(['ASSIGNED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY'])

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

        const byStatus = parcelsList.reduce((counts, parcel) => ({ ...counts, [parcel.status]: (counts[parcel.status] || 0) + 1 }), {})
        const courierStates = couriersList.reduce((counts, courier) => {
          const key = !courier.active ? 'INACTIVE' : (courier.availability || (courier.available ? 'AVAILABLE' : 'OFFLINE'))
          return { ...counts, [key]: (counts[key] || 0) + 1 }
        }, {})

        setStats({
          totalParcels: parcelsList.length,
          byStatus,
          courierStates,
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

  // Pending merchant names list
  const pendingMerchantNames = stats?.pendingMerchants
    ?.map((m) => m.businessName || m.contactName)
    ?.filter(Boolean) || []

  // Success bar percentages
  const totalFinished = (stats?.deliveredCount || 0) + (stats?.failedCount || 0)
  const deliveredPct = totalFinished > 0 ? ((stats?.deliveredCount || 0) / totalFinished) * 100 : 0
  const failedPct = totalFinished > 0 ? ((stats?.failedCount || 0) / totalFinished) * 100 : 0

  const successValue = Number(stats?.successRate) || 0

  return (
    <div className="db-page admin-dash">
      <DashboardHeader title="Operations overview"
        subtitle={`${todayLabel()}${user?.name ? ` · Welcome, ${user.name}` : ''} · Live figures from parcels, couriers and merchants.`}
        actions={<Button variant="outline" onClick={loadDashboardData}><DashboardIcon name="refresh" size={16} />Refresh</Button>} />

      {statsLoading ? (
        <div className="db-panel ad-state"><LoadingSpinner /><p role="status">Loading dashboard metrics…</p></div>
      ) : statsError ? (
        <div className="db-panel ad-state" role="alert">
          <p className="admin-error-text">{statsError}</p>
          <Button variant="secondary" onClick={loadDashboardData}>Retry</Button>
        </div>
      ) : stats ? (
        <>
          {stats.pendingApprovalsCount > 0 && (
            <section className="db-attention" aria-label="Pending approvals alert">
              <span className="db-attention-icon"><DashboardIcon name="alert" size={18} /></span>
              <div>
                <strong>
                  {stats.pendingApprovalsCount} merchant {stats.pendingApprovalsCount === 1 ? 'application is' : 'applications are'} awaiting review
                </strong>
                {pendingMerchantNames.length > 0 && (
                  <p>
                    {pendingMerchantNames.slice(0, 3).join(', ')}
                    {pendingMerchantNames.length > 3 ? ` and ${pendingMerchantNames.length - 3} more` : ''}
                  </p>
                )}
              </div>
              <Link to="/admin/merchant-approvals" className="button button-outline">Review</Link>
            </section>
          )}

          <KpiGrid label="Key operational statistics">
            <KpiCard icon="package" label="Parcels booked today" value={stats.deliveriesToday} detail={`${stats.totalParcels} parcels in the system`} />
            <KpiCard icon="truck" label="Active couriers" value={stats.activeCouriers} detail={`of ${stats.totalCouriers} couriers`} tone="success" />
            <KpiCard icon="route" label="In progress" value={stats.inProgressCount} detail="Assigned to delivery" tone="neutral" />
            <KpiCard icon="xcircle" label="Failed, last 7 days" value={stats.failedCount} detail={`${stats.deliveredCount} delivered in the same period`}
              tone="danger" highlight={stats.failedCount > 0} />
          </KpiGrid>

          <div className="db-grid-3-2">
            <Panel id="chart-deliveries" title="Deliveries per day" description="Last 7 days, by completion date">
              <DeliveriesBarChart days={stats.deliveriesPerDay} />
            </Panel>

            <Panel id="summary-deliveries" title="Delivery performance" description="Last 7 days">
              <div className="ad-performance">
                <div className="ad-ring" style={{ '--value': `${Math.min(100, Math.max(0, successValue))}%` }} role="img" aria-label={`Success rate ${stats.successRate}%`}>
                  <div><strong>{stats.successRate}%</strong><span>success rate</span></div>
                </div>
                <dl className="ad-figures">
                  <div><dt><span className="ad-dot is-success" />Delivered</dt><dd>{stats.deliveredCount}</dd></div>
                  <div><dt><span className="ad-dot is-danger" />Failed</dt><dd>{stats.failedCount}</dd></div>
                  <div><dt><span className="ad-dot is-info" />In progress</dt><dd>{stats.inProgressCount}</dd></div>
                </dl>
              </div>
              <div className="admin-progress-track ad-split" aria-hidden="true">
                {totalFinished > 0 ? (
                  <>
                    <div className="admin-progress-delivered" style={{ width: `${deliveredPct}%` }} />
                    <div className="admin-progress-failed" style={{ width: `${failedPct}%` }} />
                  </>
                ) : <div className="admin-progress-empty" />}
              </div>
              <p className="ad-caption">Success rate = delivered ÷ (delivered + failed). Parcels still in progress are not counted.</p>
            </Panel>
          </div>

          <div className="db-grid-3-2">
            <Panel id="pipeline" title="Parcels by status" description={`${stats.totalParcels} parcels`}
              actions={<Link to="/dispatch/deliveries" className="db-link">Ongoing deliveries</Link>}>
              {stats.totalParcels === 0 ? <p className="db-empty">No parcels yet.</p> : (
                <ul className="ad-pipeline">
                  {PIPELINE.filter(([status]) => stats.byStatus[status]).map(([status, label, colour]) => (
                    <li key={status}>
                      <span className="ad-pipeline-label">{label}</span>
                      <span className="ad-pipeline-bar" aria-hidden="true"><span style={{ width: `${(stats.byStatus[status] / stats.totalParcels) * 100}%`, background: colour }} /></span>
                      <strong>{stats.byStatus[status]}</strong>
                    </li>
                  ))}
                </ul>
              )}
            </Panel>

            <Panel id="courier-availability" title="Courier availability" description={`${stats.totalCouriers} courier profiles`}
              actions={<Link to="/dispatch/workload" className="db-link">Courier workload</Link>}>
              <ul className="ad-availability">
                {AVAILABILITY.map(([key, label, tone]) => (
                  <li key={key}>
                    <span className={`db-pill${tone ? ` db-pill-${tone}` : ''}`}><span className="db-pill-dot" aria-hidden="true" />{label}</span>
                    <strong>{stats.courierStates[key] || 0}</strong>
                  </li>
                ))}
              </ul>
              <div className="ad-quick">
                <Link to="/admin/courier-approvals" className="db-link">Courier approvals</Link>
                <Link to="/admin/users" className="db-link">Manage users</Link>
              </div>
            </Panel>
          </div>
        </>
      ) : null}
    </div>
  )
}
