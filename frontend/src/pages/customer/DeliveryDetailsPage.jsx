import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import LoadingSpinner from '../../components/LoadingSpinner'
import CopyTrackingNumberButton from '../../components/CopyTrackingNumberButton'
import { parcelService } from '../../services/parcelService'

const EMPTY = '—'

function humanize(value) {
  return value ? value.replaceAll('_', ' ') : EMPTY
}

function formatDateTime(value) {
  if (!value) return EMPTY
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? EMPTY
    : new Intl.DateTimeFormat('en-LK', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}

// ─── Status Configurations ──────────────────────────────────────────────────

const STATUS_CONFIG = {
  OUT_FOR_DELIVERY: {
    label: 'Out for Delivery',
    badgeClass: 'delivery-badge-out-for-delivery',
    icon: (
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <line x1="22" y1="2" x2="11" y2="13" />
        <polygon points="22 2 15 22 11 13 2 9 22 2" />
      </svg>
    ),
  },
  IN_TRANSIT: {
    label: 'In Transit',
    badgeClass: 'delivery-badge-in-transit',
    icon: (
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <rect x="1" y="3" width="15" height="13" />
        <polygon points="16 8 20 8 23 11 23 16 16 16 16 8" />
        <circle cx="5.5" cy="18.5" r="2.5" />
        <circle cx="18.5" cy="18.5" r="2.5" />
      </svg>
    ),
  },
  FAILED: {
    label: 'Failed',
    badgeClass: 'delivery-badge-failed',
    icon: (
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <circle cx="12" cy="12" r="10" />
        <line x1="15" y1="9" x2="9" y2="15" />
        <line x1="9" y1="9" x2="15" y2="15" />
      </svg>
    ),
  },
  DELIVERED: {
    label: 'Delivered',
    badgeClass: 'delivery-badge-delivered'
  },
  CREATED: {
    label: 'Created',
    badgeClass: 'delivery-badge-created',
  },
  ASSIGNED: {
    label: 'Assigned',
    badgeClass: 'delivery-badge-assigned',
  },
  PICKED_UP: {
    label: 'Picked Up',
    badgeClass: 'delivery-badge-picked-up',
  },
  CANCELLED: {
    label: 'Cancelled',
    badgeClass: 'delivery-badge-cancelled',
  },
}

// ─── Icons ───────────────────────────────────────────────────────────────────

function PackageIcon({ size = 18 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M16.5 9.4 7.55 4.24a1.78 1.78 0 0 0-2.5 1.55v12.42a1.78 1.78 0 0 0 2.5 1.55L16.5 14.6" />
      <polyline points="3.29 7 12 12 20.71 7" />
      <line x1="12" y1="22" x2="12" y2="12" />
      <path d="m21.5 14.6-8.95 5.16a1.78 1.78 0 0 1-2.5-1.55V5.79a1.78 1.78 0 0 1 2.5-1.55L21.5 9.4a1.78 1.78 0 0 1 0 3.2z" />
    </svg>
  )
}

function HistoryIcon({ size = 18 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="12" cy="12" r="10" />
      <polyline points="12 6 12 12 16 14" />
    </svg>
  )
}

function UserIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
      <circle cx="12" cy="7" r="4" />
    </svg>
  )
}

function PhoneIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" />
    </svg>
  )
}

function BuildingIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3 21h18" />
      <path d="M19 21v-4" />
      <path d="M19 17a2 2 0 0 0-2-2H7a2 2 0 0 0-2 2v4" />
      <path d="M5 21V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16" />
      <line x1="9" y1="9" x2="9" y2="9.01" />
      <line x1="15" y1="9" x2="15" y2="9.01" />
      <line x1="9" y1="13" x2="9" y2="13.01" />
      <line x1="15" y1="13" x2="15" y2="13.01" />
    </svg>
  )
}

function MapPinIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" />
      <circle cx="12" cy="10" r="3" />
    </svg>
  )
}

function ScaleIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="m16 16 3-8 3 8c-.87.65-1.92 1-3 1s-2.13-.35-3-1Z" />
      <path d="m2 16 3-8 3 8c-.87.65-1.92 1-3 1s-2.13-.35-3-1Z" />
      <path d="M7 21h10" />
      <path d="M12 3v18" />
      <path d="M3 7h2c2 0 5-1 7-2 2 1 5 2 7 2h2" />
    </svg>
  )
}

function MaximizeIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" />
      <polyline points="3.27 6.96 12 12.01 20.73 6.96" />
      <line x1="12" y1="22.08" x2="12" y2="12" />
    </svg>
  )
}

function TagIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z" />
      <line x1="7" y1="7" x2="7.01" y2="7" />
    </svg>
  )
}

function ReceiptIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M4 2v20l2-1 2 1 2-1 2 1 2-1 2 1 2-1 2 1V2l-2 1-2-1-2 1-2-1-2 1-2-1-2 1Z" />
      <line x1="8" y1="7" x2="16" y2="7" />
      <line x1="8" y1="11" x2="16" y2="11" />
      <line x1="8" y1="15" x2="12" y2="15" />
    </svg>
  )
}

function CalendarIcon({ size = 16 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
      <line x1="16" y1="2" x2="16" y2="6" />
      <line x1="8" y1="2" x2="8" y2="6" />
      <line x1="3" y1="10" x2="21" y2="10" />
    </svg>
  )
}

function ClockIcon({ size = 16 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="12" cy="12" r="10" />
      <polyline points="12 6 12 12 16 14" />
    </svg>
  )
}

// ─── Detail Component ────────────────────────────────────────────────────────

function Detail({ label, children, icon: Icon, fullWidth = false }) {
  return (
    <div className={`parcel-detail-item${fullWidth ? ' parcel-detail-item-full' : ''}`}>
      {Icon && (
        <div className="parcel-detail-icon" aria-hidden="true">
          <Icon />
        </div>
      )}
      <div className="parcel-detail-content">
        <dt>{label}</dt>
        <dd>{children ?? EMPTY}</dd>
      </div>
    </div>
  )
}

// ─── Main Component ──────────────────────────────────────────────────────────

export default function DeliveryDetailsPage() {
  const { id } = useParams()
  const [parcel, setParcel] = useState(null)
  const [history, setHistory] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const loadDetails = useCallback(() => {
    const controller = new AbortController()
    Promise.resolve()
      .then(() => {
        if (controller.signal.aborted) return null
        setLoading(true)
        setError(null)
        return Promise.all([
          parcelService.getParcelById(id, controller.signal),
          parcelService.getParcelHistory(id, controller.signal),
        ])
      })
      .then((result) => {
        if (controller.signal.aborted || result === null) return
        const [details, events] = result
        setParcel(details)
        setHistory(Array.isArray(events) ? events : [])
        setLoading(false)
      })
      .catch((requestError) => {
        if (controller.signal.aborted || requestError?.name === 'AbortError') return
        setError(requestError)
        setLoading(false)
      })
    return () => controller.abort()
  }, [id])

  useEffect(() => loadDetails(), [loadDetails])

  if (loading) {
    return (
      <div className="card status-card status-card-loading">
        <LoadingSpinner />
        <p role="status">Loading delivery details…</p>
      </div>
    )
  }

  if (error) {
    return (
      <div className="card status-card" role="alert">
        <h1 className="card-title">Unable to load delivery</h1>
        <p className="card-subtitle">{error.message || 'The parcel details are unavailable.'}</p>
        <button type="button" className="btn btn-primary" onClick={loadDetails}>Try again</button>
        <Link className="btn btn-secondary" to="/customer/deliveries">Back to deliveries</Link>
      </div>
    )
  }

  const statusCfg = STATUS_CONFIG[parcel.status] ?? {
    badgeClass: 'delivery-badge-cancelled',
    icon: (
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <circle cx="12" cy="12" r="10" />
      </svg>
    ),
  }

  return (
    <div className="delivery-page-container parcel-details-page">
      <nav className="delivery-breadcrumb" aria-label="Breadcrumb">
        <Link className="delivery-breadcrumb-muted" to="/customer/deliveries">
          My Deliveries
        </Link>
        <span className="delivery-breadcrumb-sep" aria-hidden="true">&rsaquo;</span>
      </nav>

      <div className="delivery-header-row parcel-details-header">
        <div className="parcel-details-header-info">
          <h1 className="delivery-page-title">Delivery Details</h1>
          <div className="parcel-details-tracking-row">
            <div className="parcel-tracking-code-pill">
              <span className="parcel-tracking-prefix">Tracking </span>
              <span className="parcel-details-tracking">{parcel.trackingNumber}</span>
            </div>
            <CopyTrackingNumberButton trackingNumber={parcel.trackingNumber} />
          </div>
        </div>
        <div className="parcel-details-header-badge">
          <span className={`parcel-details-status ${statusCfg.badgeClass}`}>
            {statusCfg.icon}
            <span>{humanize(parcel.status)}</span>
          </span>
        </div>
      </div>

      <div className="parcel-details-layout">
        {/* Left Column: Parcel Information */}
        <section className="delivery-table-card parcel-details-card" aria-labelledby="parcel-information-title">
          <div className="parcel-card-header">
            <div className="parcel-card-header-icon" aria-hidden="true">
              <PackageIcon />
            </div>
            <div>
              <h2 id="parcel-information-title" className="parcel-card-title">Parcel information</h2>
              <p className="parcel-card-subtitle">Complete shipment and recipient details</p>
            </div>
          </div>
          <dl className="parcel-details-grid">
            <Detail label="Receiver" icon={UserIcon}>{parcel.receiverName}</Detail>
            <Detail label="Receiver phone" icon={PhoneIcon}>{parcel.receiverPhone}</Detail>
            <Detail label="Pickup address" icon={BuildingIcon} fullWidth>{parcel.pickupAddress}</Detail>
            <Detail label="Delivery address" icon={MapPinIcon} fullWidth>{parcel.deliveryAddress}</Detail>
            <Detail label="Weight" icon={ScaleIcon}>{parcel.weight != null ? `${parcel.weight} kg` : EMPTY}</Detail>
            <Detail label="Size" icon={MaximizeIcon}>{parcel.size}</Detail>
            <Detail label="Parcel type" icon={TagIcon}>{humanize(parcel.parcelType)}</Detail>
            <Detail label="Delivery fee" icon={ReceiptIcon}>{parcel.charge != null ? `LKR ${Number(parcel.charge).toFixed(2)}` : EMPTY}</Detail>
            <Detail label="Created" icon={CalendarIcon}>{formatDateTime(parcel.createdAt)}</Detail>
            <Detail label="Last updated" icon={ClockIcon}>{formatDateTime(parcel.updatedAt)}</Detail>
          </dl>
        </section>

        {/* Right Column: Status History Timeline */}
        <section className="delivery-table-card parcel-details-card" aria-labelledby="parcel-history-title">
          <div className="parcel-card-header">
            <div className="parcel-card-header-icon" aria-hidden="true">
              <HistoryIcon />
            </div>
            <div>
              <h2 id="parcel-history-title" className="parcel-card-title">Status history</h2>
              <p className="parcel-card-subtitle">Tracking progress updates</p>
            </div>
          </div>
          {history.length === 0 ? (
            <div className="parcel-history-empty-state">
              <div className="parcel-history-empty-icon" aria-hidden="true">
                <ClockIcon size={22} />
              </div>
              <p className="parcel-history-empty">No status events have been recorded.</p>
            </div>
          ) : (
            <ol className="parcel-history-timeline">
              {history.map((event, index) => {
                const isLatest = index === 0
                const eventCfg = STATUS_CONFIG[event.newStatus]
                return (
                  <li key={event.id} className={`parcel-timeline-item${isLatest ? ' is-latest' : ''}`}>
                    <span className="parcel-timeline-dot" aria-hidden="true" />
                    <div className="parcel-timeline-content">
                      <div className="timeline-status">
                        <span className={`parcel-timeline-status-badge ${eventCfg?.badgeClass || 'delivery-badge-cancelled'}`}>
                          {eventCfg?.icon}
                          <span>{humanize(event.newStatus)}</span>
                        </span>
                      </div>
                      <time className="timeline-time" dateTime={event.changedAt}>
                        <CalendarIcon size={12} />
                        <span>{formatDateTime(event.changedAt)}</span>
                      </time>
                      {event.note && <div className="timeline-note">{event.note}</div>}
                    </div>
                  </li>
                )
              })}
            </ol>
          )}
        </section>
      </div>
    </div>
  )
}
