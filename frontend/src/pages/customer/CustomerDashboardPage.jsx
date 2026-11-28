import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import Button, { ButtonLink } from '../../components/Button'
import LoadingSpinner from '../../components/LoadingSpinner'
import TrackingSearch from '../../components/TrackingSearch'
import { useAuth } from '../../context/AuthContext'
import { parcelService } from '../../services/parcelService'
import { DashboardHeader, KpiCard, KpiGrid, Panel } from '../../components/dashboard/Dashboard'
import { todayLabel } from '../../utils/dashboardFormat'
import DashboardIcon from '../../components/dashboard/DashboardIcon'
import './customerDashboard.css'

// Order and colours for the "Your shipments" breakdown.
const BREAKDOWN = [
  ['CREATED', 'Awaiting pickup', '#94a3b8'],
  ['PICKED_UP', 'Picked up', '#38bdf8'],
  ['IN_TRANSIT', 'In transit', '#1c5dfb'],
  ['OUT_FOR_DELIVERY', 'Out for delivery', '#f59e0b'],
  ['DELIVERED', 'Delivered', '#16a34a'],
  ['FAILED', 'Failed', '#dc2626'],
  ['CANCELLED', 'Cancelled', '#cbd5e1'],
]
const firstName = (name) => name?.trim().split(/\s+/)[0] || 'there'

const STATUS_CONFIG = {
  OUT_FOR_DELIVERY: {
    label: 'Out for Delivery',
    badgeClass: 'delivery-badge delivery-badge-out-for-delivery',
    icon: (
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <line x1="22" y1="2" x2="11" y2="13" />
        <polygon points="22 2 15 22 11 13 2 9 22 2" />
      </svg>
    ),
  },
  IN_TRANSIT: {
    label: 'In Transit',
    badgeClass: 'delivery-badge delivery-badge-in-transit',
    icon: (
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <rect x="1" y="3" width="15" height="13" />
        <polygon points="16 8 20 8 23 11 23 16 16 16 8" />
        <circle cx="5.5" cy="18.5" r="2.5" />
        <circle cx="18.5" cy="18.5" r="2.5" />
      </svg>
    ),
  },
  DELIVERED: {
    label: 'Delivered',
    badgeClass: 'delivery-badge delivery-badge-delivered',
    icon: (
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <circle cx="12" cy="12" r="10" />
        <polyline points="9 11 11.5 13.5 15.5 9.5" />
      </svg>
    ),
  },
  FAILED: {
    label: 'Failed',
    badgeClass: 'delivery-badge delivery-badge-failed',
    icon: (
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <circle cx="12" cy="12" r="10" />
        <line x1="15" y1="9" x2="9" y2="15" />
        <line x1="9" y1="9" x2="15" y2="15" />
      </svg>
    ),
  },
  CREATED: {
    label: 'Created',
    badgeClass: 'delivery-badge delivery-badge-created',
    icon: (
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <circle cx="12" cy="12" r="10" />
        <polyline points="12 6 12 12 16 14" />
      </svg>
    ),
  },
  ASSIGNED: {
    label: 'Assigned',
    badgeClass: 'delivery-badge delivery-badge-assigned',
    icon: (
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
        <circle cx="8.5" cy="7" r="4" />
        <polyline points="17 11 19 13 23 9" />
      </svg>
    ),
  },
  PICKED_UP: {
    label: 'Picked Up',
    badgeClass: 'delivery-badge delivery-badge-picked-up',
    icon: (
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M16.5 9.4 7.55 4.24a1.78 1.78 0 0 0-1.8 0l-3.3 1.9A1.8 1.8 0 0 0 1.5 7.7v8.6a1.8 1.8 0 0 0 .95 1.57l3.3 1.9c.55.32 1.25.32 1.8 0l8.95-5.16" />
        <polyline points="3.29 7 12 12 20.71 7" />
        <line x1="12" y1="22" x2="12" y2="12" />
        <polyline points="16 16 18 18 22 14" />
      </svg>
    ),
  },
  CANCELLED: {
    label: 'Cancelled',
    badgeClass: 'delivery-badge delivery-badge-cancelled',
    icon: (
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <circle cx="12" cy="12" r="10" />
        <line x1="4.93" y1="4.93" x2="19.07" y2="19.07" />
      </svg>
    ),
  },
}

function formatDate(value) {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return '—'
  const day = String(d.getDate()).padStart(2, '0')
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
  const month = months[d.getMonth()]
  const year = d.getFullYear()
  return `${day} ${month} ${year}`
}

const RECENT_COUNT = 5

function formatSubtitle(parcel) {
  const receiver = parcel.receiverName ? parcel.receiverName.trim() : ''
  const destination = parcel.deliveryAddress ? parcel.deliveryAddress.trim() : ''
  const parts = []
  if (receiver) parts.push(receiver)
  if (destination) parts.push(destination)
  const recipient = parts.join(', ')
  const dateStr = formatDate(parcel.createdAt)

  if (recipient && dateStr !== '—') {
    return `To ${recipient} · ${dateStr}`
  }
  if (recipient) {
    return `To ${recipient}`
  }
  if (dateStr !== '—') {
    return dateStr
  }
  return '—'
}

export default function CustomerDashboardPage() {
  const { user } = useAuth()
  const [parcels, setParcels] = useState([])
  const [summary, setSummary] = useState({ total: 0, byStatus: {} })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const loadData = useCallback(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(null)

    // Counts come from the server so they cover every parcel, not just the
    // few recent ones listed below.
    Promise.all([
      parcelService.getMyParcelSummary(controller.signal),
      parcelService.getMyParcels(0, RECENT_COUNT, controller.signal),
    ])
      .then(([counts, data]) => {
        if (controller.signal.aborted) return
        const list = Array.isArray(data)
          ? data
          : Array.isArray(data?.content)
            ? data.content
            : []
        setSummary({
          total: Number(counts?.total) || 0,
          byStatus: counts?.byStatus ?? {},
        })
        setParcels(list)
        setLoading(false)
      })
      .catch((err) => {
        if (!controller.signal.aborted && err?.name !== 'AbortError') {
          setError(
            err?.message ||
            'Unable to load your deliveries. Please check your connection and try again.'
          )
          setLoading(false)
        }
      })

    return () => controller.abort()
  }, [])

  useEffect(() => {
    return loadData()
  }, [loadData])

  const stats = useMemo(() => {
    const count = (status) => Number(summary.byStatus?.[status]) || 0
    return {
      total: summary.total,
      active: count('PICKED_UP') + count('IN_TRANSIT') + count('OUT_FOR_DELIVERY'),
      delivered: count('DELIVERED'),
      pending: count('CREATED'),
      failed: count('FAILED'),
      count,
    }
  }, [summary])
  const finished = stats.delivered + stats.failed
  const successRate = finished ? Math.round((stats.delivered / finished) * 100) : null

  // Latest parcels for the Recent Deliveries section
  const recentParcels = parcels

  const header = (
    <DashboardHeader title={`Welcome back, ${firstName(user?.name)}`}
      subtitle={`${todayLabel()} · Book, track and manage your deliveries.`}
      actions={<>
        <ButtonLink to="/customer/deliveries" variant="outline"><DashboardIcon name="inbox" size={16} />View all deliveries</ButtonLink>
        <ButtonLink to="/customer/create-delivery"><DashboardIcon name="plus" size={16} />Create delivery</ButtonLink>
      </>} />
  )

  return (
    <div className="db-page customer-dash">
      {header}

      {loading ? (
        <div className="db-panel cd-state"><LoadingSpinner /><p role="status">Loading your dashboard…</p></div>
      ) : error ? (
        <div className="db-panel cd-state" role="alert">
          <h2>Unable to load dashboard</h2>
          <p>{error}</p>
          <Button onClick={loadData}>Try again</Button>
        </div>
      ) : summary.total === 0 ? (
        <div className="db-grid-3-2">
          <section className="db-panel cd-empty" aria-label="No deliveries yet">
            <span className="cd-empty-icon" aria-hidden="true"><DashboardIcon name="package" size={30} /></span>
            <h2>No deliveries booked yet</h2>
            <p>Book your first parcel and follow it here from pickup to delivery.</p>
            <ButtonLink to="/customer/create-delivery">Book your first delivery</ButtonLink>
          </section>
          <Panel id="cd-track-empty" title="Track a parcel" description="Have a tracking number from someone else? Look it up here.">
            <TrackingSearch id="dashboard-empty-tracking-search" variant="card" label="Tracking number" placeholder="SD10480014"
              buttonText="Track parcel" helperText="SD followed by 8 digits, e.g. SD10480014." />
          </Panel>
        </div>
      ) : (
        <>
          <KpiGrid label="Delivery statistics">
            <KpiCard icon="package" label="Total shipments" value={stats.total} detail="All your bookings" />
            <KpiCard icon="truck" label="On the way" value={stats.active} detail="Picked up and moving" tone="warning" />
            <KpiCard icon="check" label="Delivered" value={stats.delivered} detail={successRate == null ? 'None finished yet' : `${successRate}% delivered successfully`} tone="success" />
            <KpiCard icon="clock" label="Awaiting pickup" value={stats.pending} detail="Booked, courier on the way" tone="neutral" />
          </KpiGrid>

          <div className="db-grid-3-2">
            <Panel id="cd-recent" title="Recent deliveries" flush
              actions={<Link to="/customer/deliveries" className="db-link">View all</Link>}>
              {recentParcels.length === 0 ? (
                <p className="db-empty">No recent deliveries found.</p>
              ) : (
                <ul className="cd-recent">
                  {recentParcels.map((parcel) => {
                    const cfg = STATUS_CONFIG[parcel.status] ?? { label: parcel.status ?? '—', badgeClass: 'delivery-badge delivery-badge-cancelled', icon: null }
                    const targetUrl = parcel.id
                      ? `/customer/deliveries/${parcel.id}`
                      : parcel.trackingNumber ? `/track/${encodeURIComponent(parcel.trackingNumber)}` : '/customer/deliveries'
                    return (
                      <li key={parcel.id || parcel.trackingNumber}>
                        <Link to={targetUrl} className="cd-recent-row">
                          <span className="cd-recent-icon" aria-hidden="true"><DashboardIcon name="package" size={17} /></span>
                          <span className="cd-recent-main">
                            <span className="cd-recent-tracking">{parcel.trackingNumber || '—'}</span>
                            <span className="cd-recent-meta">{formatSubtitle(parcel)}</span>
                          </span>
                          <span className="cd-recent-side">
                            <span className={cfg.badgeClass}>{cfg.icon}<span>{cfg.label}</span></span>
                            <span className="cd-recent-fee">{parcel.charge != null ? `LKR ${Number(parcel.charge).toLocaleString()}` : '—'}</span>
                          </span>
                          <DashboardIcon name="chevronRight" size={16} className="cd-recent-arrow" />
                        </Link>
                      </li>
                    )
                  })}
                </ul>
              )}
            </Panel>

            <div className="cd-side">
              <Panel id="cd-track" title="Track a parcel" description="Enter a tracking number to see where it is.">
                <TrackingSearch id="dashboard-tracking-search" variant="card" label="Tracking number" placeholder="SD10480014"
                  buttonText="Track" helperText="SD followed by 8 digits, e.g. SD10480014." />
              </Panel>
              <Panel id="cd-breakdown" title="Your shipments" description={`${stats.total} in total, by current status`}>
                <div className="cd-stack" aria-hidden="true">
                  {BREAKDOWN.map(([status, , colour]) => stats.count(status) > 0 && (
                    <span key={status} style={{ width: `${(stats.count(status) / stats.total) * 100}%`, background: colour }} />
                  ))}
                </div>
                <ul className="cd-legend">
                  {BREAKDOWN.filter(([status]) => stats.count(status) > 0).map(([status, label, colour]) => (
                    <li key={status}><span className="cd-dot" style={{ background: colour }} />{label}<strong>{stats.count(status)}</strong></li>
                  ))}
                </ul>
              </Panel>
            </div>
          </div>
        </>
      )}
    </div>
  )
}
