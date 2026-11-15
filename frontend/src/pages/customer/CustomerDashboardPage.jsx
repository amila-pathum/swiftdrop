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
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <line x1="5" y1="12" x2="19" y2="12" />
        <polyline points="12 5 19 12 12 19" />
      </svg>
    ),
  },
  PICKED_UP: {
    label: 'Picked Up',
    badgeClass: 'delivery-badge delivery-badge-picked-up',
    icon: (
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <polyline points="18 15 12 9 6 15" />
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

export default function CustomerDashboardPage() {
  const { user } = useAuth()
  const [parcels, setParcels] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const loadData = useCallback(() => {
    let ignore = false
    setLoading(true)
    setError(null)

    parcelService
      .getMyParcels()
      .then((data) => {
        if (!ignore) {
          const list = Array.isArray(data)
            ? data
            : Array.isArray(data?.content)
              ? data.content
              : []
          setParcels(list)
          setLoading(false)
        }
      })
      .catch((err) => {
        if (!ignore && err?.name !== 'AbortError') {
          setError(
            err?.message ||
            'Unable to load your deliveries. Please check your connection and try again.'
          )
          setLoading(false)
        }
      })

    return () => {
      ignore = true
    }
  }, [])

  useEffect(() => {
    return loadData()
  }, [loadData])

  // Aggregate statistics computed from real parcel data
  const stats = useMemo(() => {
    const total = parcels.length
    let active = 0
    let delivered = 0
    let pending = 0

    for (const p of parcels) {
      const s = p.status
      if (s === 'DELIVERED') {
        delivered += 1
      } else if (['ASSIGNED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY'].includes(s)) {
        active += 1
      } else if (s === 'CREATED') {
        pending += 1
      }
    }

    return { total, active, delivered, pending }
  }, [parcels])

  // Latest 5 parcels for the Recent Deliveries section
  const recentParcels = useMemo(() => {
    return parcels.slice(0, 5)
  }, [parcels])

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
      ) : parcels.length === 0 ? (
        /* Empty State */
        <>
          {/* Quick Parcel Tracking Search Card */}
          <section className="customer-track-card" aria-label="Quick parcel tracking">
            <div className="customer-track-content">
              <div className="customer-track-info">
                <span className="customer-track-icon" aria-hidden="true">
                  <svg
                    width="16"
                    height="16"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2.2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  >
                    <circle cx="11" cy="11" r="8" />
                    <line x1="21" y1="21" x2="16.65" y2="16.65" />
                  </svg>
                </span>
                <span className="customer-track-label">
                  <strong>Track a package:</strong> Enter tracking number for live status
                </span>
              </div>
              <div className="customer-track-input-wrap">
                <TrackingSearch id="dashboard-empty-tracking-search" size="sm" placeholder="Enter Tracking Number" />
              </div>
            </div>
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

          {/* Quick Parcel Tracking Search Card */}
          <section className="customer-track-card" aria-label="Quick parcel tracking">
            <div className="customer-track-content">
              <div className="customer-track-info">
                <h2 className="customer-track-title">
                  <svg
                    width="18"
                    height="18"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    aria-hidden="true"
                    style={{ color: 'var(--color-primary)' }}
                  >
                    <circle cx="11" cy="11" r="8" />
                    <line x1="21" y1="21" x2="16.65" y2="16.65" />
                  </svg>
                  <span>Quick Parcel Tracking</span>
                </h2>
                <p className="customer-track-desc">
                  Have a tracking number? Enter it below to check instant live delivery status.
                </p>
              </div>
              <div className="customer-track-input-wrap">
                <TrackingSearch id="dashboard-tracking-search" size="md" />
              </div>
            </div>
          </section>

          {/* Recent Deliveries Table Card */}
          <section className="delivery-table-card" aria-label="Recent deliveries">
            <div
              className="card-table-header"
              style={{
                padding: '16px 20px',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                flexWrap: 'wrap',
                gap: '12px',
              }}
            >
              <div>
                <h2 style={{ fontSize: '16px', fontWeight: 700, margin: 0, color: '#0f172a' }}>
                  Recent Deliveries
                </h2>
                <p style={{ fontSize: '13px', color: '#64748b', margin: '2px 0 0' }}>
                  Your latest {recentParcels.length} shipments and their current delivery progress
                </p>
              </div>
              <Link
                to="/customer/deliveries"
                style={{
                  fontSize: '13px',
                  fontWeight: 600,
                  color: 'var(--color-primary)',
                  textDecoration: 'none',
                }}
              >
                View all deliveries →
              </Link>
            </div>

            <div className="delivery-table-wrapper">
              <table className="delivery-table">
                <thead>
                  <tr>
                    <th scope="col">Tracking no.</th>
                    <th scope="col">Destination</th>
                    <th scope="col">Created</th>
                    <th scope="col">Fee</th>
                    <th scope="col">Status</th>
                    <th scope="col" aria-label="Actions"></th>
                  </tr>
                </thead>
                <tbody>
                  {recentParcels.map((parcel) => {
                    const cfg = STATUS_CONFIG[parcel.status] ?? {
                      label: parcel.status ?? '—',
                      badgeClass: 'delivery-badge delivery-badge-cancelled',
                      icon: null,
                    }
                    return (
                      <tr key={parcel.id}>
                        {/* Tracking number */}
                        <td>
                          <span className="delivery-tracking-num">
                            {parcel.trackingNumber ?? '—'}
                          </span>
                        </td>

                        {/* Destination & Recipient */}
                        <td>
                          <div className="delivery-destination-name">
                            {parcel.deliveryAddress ?? '—'}
                          </div>
                          {parcel.receiverName && (
                            <div className="delivery-destination-sub">
                              {parcel.receiverName}
                            </div>
                          )}
                        </td>

                        {/* Created Date */}
                        <td className="delivery-date">
                          {formatDate(parcel.createdAt)}
                        </td>

                        {/* Fee */}
                        <td className="delivery-fee">
                          {parcel.charge != null
                            ? `LKR ${Number(parcel.charge).toLocaleString()}`
                            : '—'}
                        </td>

                        {/* Status Badge */}
                        <td>
                          <span className={cfg.badgeClass}>
                            {cfg.icon}
                            <span>{cfg.label}</span>
                          </span>
                        </td>

                        {/* View Details Action */}
                        <td>
                          <div className="delivery-actions-cell">
                            <Link
                              to={
                                parcel.trackingNumber
                                  ? `/track/${encodeURIComponent(parcel.trackingNumber)}`
                                  : '/customer/deliveries'
                              }
                              className="delivery-btn-view-details"
                            >
                              View Details
                            </Link>
                          </div>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          </section>
        </>
      )}
    </div>
  )
}
