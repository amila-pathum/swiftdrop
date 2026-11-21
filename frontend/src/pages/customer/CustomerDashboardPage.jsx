import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import Button, { ButtonLink } from '../../components/Button'
import LoadingSpinner from '../../components/LoadingSpinner'
import TrackingSearch from '../../components/TrackingSearch'
import { useAuth } from '../../context/AuthContext'
import { parcelService } from '../../services/parcelService'

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
    }
  }, [summary])

  // Latest parcels for the Recent Deliveries section
  const recentParcels = parcels

  return (
    <div className="customer-dashboard-container">
      {/* Welcome Header */}
      <header className="customer-dashboard-header">
        <div className="customer-dashboard-header-text">
          <div className="customer-dashboard-greeting-row">
            <h1 className="customer-dashboard-title">
              Welcome Back, {user?.name || 'Customer'}
            </h1>
            <span className="profile-badge-active" style={{ height: 'fit-content' }}>
              <span className="profile-status-dot" aria-hidden="true" />
              {user?.status || 'ACTIVE'}
            </span>
          </div>
        </div>

        <div className="customer-dashboard-header-actions">
          <ButtonLink to="/customer/deliveries" variant="outline">
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
              <polyline points="14 2 14 8 20 8" />
              <line x1="16" y1="13" x2="8" y2="13" />
              <line x1="16" y1="17" x2="8" y2="17" />
              <polyline points="10 9 9 9 8 9" />
            </svg>
            <span>View All Deliveries</span>
          </ButtonLink>

          <ButtonLink to="/customer/create-delivery" variant="primary">
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <line x1="12" y1="5" x2="12" y2="19" />
              <line x1="5" y1="12" x2="19" y2="12" />
            </svg>
            <span>Create Delivery</span>
          </ButtonLink>
        </div>
      </header>

      {/* Loading State */}
      {loading ? (
        <div className="card status-card status-card-loading" style={{ margin: '40px auto' }}>
          <LoadingSpinner />
          <p role="status">Loading your dashboard…</p>
        </div>
      ) : error ? (
        /* Error State */
        <div className="card status-card" role="alert" style={{ margin: '40px auto' }}>
          <h2 className="card-title">Unable to load dashboard</h2>
          <p className="card-subtitle">{error}</p>
          <div style={{ marginTop: '16px' }}>
            <Button onClick={loadData}>Try again</Button>
          </div>
        </div>
      ) : summary.total === 0 ? (
        /* Empty State */
        <>
          {/* Tracking Search Card */}
          <section className="customer-track-card" aria-label="Tracking search">
            <TrackingSearch
              id="dashboard-empty-tracking-search"
              variant="card"
              label="Tracking number"
              placeholder="SD10480014"
              buttonText="Track Parcel"
              helperText="Format: SD followed by 8 digits, e.g. SD10480014. Find it in your booking confirmation."
            />
          </section>

          {/* Welcoming Empty State Card */}
          <section className="customer-dashboard-empty-card" aria-label="No deliveries yet">
            <div className="customer-dashboard-empty-icon" aria-hidden="true">
              <svg
                width="36"
                height="36"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.7"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" />
                <polyline points="3.27 6.96 12 12.01 20.73 6.96" />
                <line x1="12" y1="22.08" x2="12" y2="12" />
              </svg>
            </div>
            <h2 className="customer-dashboard-empty-title">No deliveries booked yet</h2>
            <p className="customer-dashboard-empty-desc">
              You haven't booked any parcel deliveries yet.
            </p>
            <div style={{ marginTop: '20px' }}>
              <ButtonLink to="/customer/create-delivery" variant="primary">
                <span>Book Your First Delivery</span>
              </ButtonLink>
            </div>
          </section>
        </>
      ) : (
        /* Active Customer Dashboard Content */
        <>
          {/* Statistics Cards Grid */}
          <section aria-label="Delivery statistics">
            <div className="stats-grid">
              <div className="stat-card stat-card-blue">
                <span className="stat-label">Total Shipments</span>
                <span className="stat-value">{stats.total}</span>
                <span className="stat-subtext">All recorded bookings</span>
              </div>

              <div className="stat-card stat-card-amber">
                <span className="stat-label">Active Deliveries</span>
                <span className="stat-value">{stats.active}</span>
                <span className="stat-subtext">In transit or out for delivery</span>
              </div>

              <div className="stat-card stat-card-green">
                <span className="stat-label">Delivered</span>
                <span className="stat-value">{stats.delivered}</span>
                <span className="stat-subtext">Successfully completed</span>
              </div>

              <div className="stat-card stat-card-blue">
                <span className="stat-label">Pending Bookings</span>
                <span className="stat-value">{stats.pending}</span>
                <span className="stat-subtext">Awaiting pickup & assignment</span>
              </div>
            </div>
          </section>

          {/* Tracking Search Card */}
          <section className="customer-track-card" aria-label="Tracking search">
            <TrackingSearch
              id="dashboard-tracking-search"
              variant="card"
              label="Tracking number"
              placeholder="SD10480014"
              buttonText="Track Parcel"
              helperText="Format: SD followed by 8 digits, e.g. SD10480014. Find it in your booking confirmation."
            />
          </section>

          {/* Recent Deliveries Section */}
          <section className="recent-deliveries-section" aria-label="Recent Deliveries">
            <div className="recent-deliveries-header">
              <h2 className="recent-deliveries-title">Recent Deliveries</h2>
              <Link to="/customer/deliveries" className="recent-deliveries-view-all" style={{ textDecoration: 'none' }}>
                View All
              </Link>
            </div>

            <div className="recent-deliveries-card">
              {recentParcels.length === 0 ? (
                <div className="recent-deliveries-empty">
                  <p>No recent deliveries found.</p>
                </div>
              ) : (
                recentParcels.map((parcel) => {
                const cfg = STATUS_CONFIG[parcel.status] ?? {
                  label: parcel.status ?? '—',
                  badgeClass: 'delivery-badge delivery-badge-cancelled',
                  icon: null,
                }
                const targetUrl = parcel.id
                  ? `/customer/deliveries/${parcel.id}`
                  : parcel.trackingNumber
                    ? `/track/${encodeURIComponent(parcel.trackingNumber)}`
                    : '/customer/deliveries'

                return (
                  <Link
                    key={parcel.id || parcel.trackingNumber}
                    to={targetUrl}
                    className="recent-delivery-row"
                    style={{ textDecoration: 'none' }}
                  >
                    <div className="recent-delivery-main">
                      <span className="recent-delivery-tracking">
                        {parcel.trackingNumber || '—'}
                      </span>
                      <span className="recent-delivery-meta">
                        {formatSubtitle(parcel)}
                      </span>
                    </div>

                    <div className="recent-delivery-extra">
                      <span className="recent-delivery-fee">
                        {parcel.charge != null
                          ? `LKR ${Number(parcel.charge).toLocaleString()}`
                          : '—'}
                      </span>

                      <div className="recent-delivery-badge-wrap">
                        <span className={cfg.badgeClass}>
                          {cfg.icon}
                          <span>{cfg.label}</span>
                        </span>
                      </div>

                      <svg
                        className="recent-delivery-arrow"
                        width="16"
                        height="16"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="2"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        aria-hidden="true"
                      >
                        <polyline points="9 18 15 12 9 6" />
                      </svg>
                    </div>
                  </Link>
                )
              }))}
            </div>
          </section>
        </>
      )}
    </div>
  )
}
