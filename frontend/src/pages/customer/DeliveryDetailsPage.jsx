import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import LoadingSpinner from '../../components/LoadingSpinner'
import { parcelService } from '../../services/parcelService'

const EMPTY = '—'

function humanize(value) {
  if (!value) return EMPTY
  return value
    .toLowerCase()
    .replaceAll('_', ' ')
    .replace(/\b\w/g, (c) => c.toUpperCase())
}

function formatDateTime(value) {
  if (!value) return EMPTY
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? EMPTY
    : new Intl.DateTimeFormat('en-LK', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}

function formatDate(value) {
  if (!value) return EMPTY
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? EMPTY
    : new Intl.DateTimeFormat('en-LK', { dateStyle: 'medium' }).format(date)
}

// ─── Status Configurations ──────────────────────────────────────────────────

const STATUS_CONFIG = {
  CREATED: {
    label: 'Created',
    badgeClass: 'delivery-badge-created',
    icon: (
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <circle cx="12" cy="12" r="10" />
        <polyline points="12 6 12 12 16 14" />
      </svg>
    ),
  },
  ASSIGNED: {
    label: 'Assigned',
    badgeClass: 'delivery-badge-assigned',
    icon: (
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
        <circle cx="12" cy="7" r="4" />
        <polyline points="16 11 18 13 22 9" />
      </svg>
    ),
  },
  PICKED_UP: {
    label: 'Picked Up',
    badgeClass: 'delivery-badge-picked-up',
    icon: (
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" />
        <polyline points="9 12 12 15 15 12" />
        <line x1="12" y1="8" x2="12" y2="15" />
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
  DELIVERED: {
    label: 'Delivered',
    badgeClass: 'delivery-badge-delivered',
    icon: (
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
        <polyline points="22 4 12 14.01 9 11.01" />
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
  CANCELLED: {
    label: 'Cancelled',
    badgeClass: 'delivery-badge-cancelled',
    icon: (
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <circle cx="12" cy="12" r="10" />
        <line x1="4.93" y1="4.93" x2="19.07" y2="19.07" />
      </svg>
    ),
  },
}

// ─── Progress stepper order ───────────────────────────────────────────────────

const PROGRESS_STEPS = [
  'CREATED',
  'PICKED_UP',
  'IN_TRANSIT',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
]

// ─── SVG Icons ────────────────────────────────────────────────────────────────

function CheckIcon() {
  return (
    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <polyline points="20 6 9 17 4 12" />
    </svg>
  )
}

function MapPinIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" />
      <circle cx="12" cy="10" r="3" />
    </svg>
  )
}

function PhoneIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" />
    </svg>
  )
}

// ─── Status Badge ─────────────────────────────────────────────────────────────

function StatusBadge({ status, size = 'sm' }) {
  const cfg = STATUS_CONFIG[status] ?? { label: humanize(status), badgeClass: 'delivery-badge-cancelled', icon: null }
  return (
    <span className={`ddp-badge ddp-badge-${size} ${cfg.badgeClass}`}>
      {cfg.icon}
      <span>{cfg.label}</span>
    </span>
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

  // ── Loading ──
  if (loading) {
    return (
      <div className="card status-card status-card-loading">
        <LoadingSpinner />
        <p role="status">Loading delivery details…</p>
      </div>
    )
  }

  // ── Error ──
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

  // ─── Derived values ───────────────────────────────────────────────────────

  // Build a map of status -> first history event for that status (for progress stepper)
  const historyByStatus = {}
  ;[...history].reverse().forEach((evt) => {
    historyByStatus[evt.newStatus] = evt
  })

  // Determine current progress step index
  const currentStepIndex = (() => {
    // Find the latest PROGRESS_STEPS status that appears in history
    let idx = -1
    history.forEach((evt) => {
      const i = PROGRESS_STEPS.indexOf(evt.newStatus)
      if (i > idx) idx = i
    })
    return idx
  })()

  // For terminal statuses (FAILED, CANCELLED), use those for display
  const isTerminal = ['FAILED', 'CANCELLED'].includes(parcel.status)

  // Sort history oldest first for display in table
  const historyAsc = [...history].reverse()

  // Size label formatter
  function sizeLabel(size) {
    if (!size) return EMPTY
    const s = size.toUpperCase()
    if (s === 'SMALL') return `Small`
    if (s === 'MEDIUM') return `Medium`
    if (s === 'LARGE') return `Large`
    return humanize(size)
  }

  // Actor: CREATED = Customer, others = Courier
  function getActor(status) {
    return status === 'CREATED' ? 'Customer' : 'Courier'
  }

  // ─── Render ──────────────────────────────────────────────────────────────

  return (
    <div className="ddp-page">

      {/* Breadcrumb */}
      <nav className="ddp-breadcrumb" aria-label="Breadcrumb">
        <Link className="ddp-breadcrumb-link" to="/customer/deliveries">My Deliveries</Link>
        <span className="ddp-breadcrumb-sep" aria-hidden="true">›</span>
        <span className="ddp-breadcrumb-current">{parcel.trackingNumber}</span>
      </nav>

      {/* Page title + status row */}
      <div className="ddp-title-block">
        <h1 className="ddp-tracking-title">{parcel.trackingNumber}</h1>
        <div className="ddp-title-meta">
          <StatusBadge status={parcel.status} size="md" />
          <span className="ddp-booked-date">Booked {formatDate(parcel.createdAt)}, {new Intl.DateTimeFormat('en-LK', { timeStyle: 'short' }).format(new Date(parcel.createdAt))}</span>
        </div>
      </div>

      {/* Two-column layout */}
      <div className="ddp-two-col">

        {/* ── LEFT COLUMN ── */}
        <div className="ddp-left">

          {/* Pickup card */}
          <section className="ddp-card" aria-labelledby="ddp-pickup-title">
            <h2 className="ddp-card-title" id="ddp-pickup-title">Pickup</h2>
            <div className="ddp-address-block">
              <div className="ddp-address-row">
                <MapPinIcon />
                <span>{parcel.pickupAddress || EMPTY}</span>
              </div>
            </div>
          </section>

          {/* Receiver card */}
          <section className="ddp-card" aria-labelledby="ddp-receiver-title">
            <h2 className="ddp-card-title" id="ddp-receiver-title">Receiver</h2>
            <div className="ddp-address-block">
              <div className="ddp-contact-name">{parcel.receiverName || EMPTY}</div>
              {parcel.receiverPhone && (
                <div className="ddp-address-row ddp-contact-phone">
                  <PhoneIcon />
                  <span>{parcel.receiverPhone}</span>
                </div>
              )}
              <div className="ddp-address-row">
                <MapPinIcon />
                <span>{parcel.deliveryAddress || EMPTY}</span>
              </div>
            </div>
          </section>

          {/* Parcel card */}
          <section className="ddp-card" aria-labelledby="ddp-parcel-title">
            <h2 className="ddp-card-title" id="ddp-parcel-title">Parcel</h2>
            <table className="ddp-parcel-table">
              <tbody>
                <tr>
                  <td className="ddp-parcel-label">Description</td>
                  <td className="ddp-parcel-value">{parcel.parcelType ? humanize(parcel.parcelType) : EMPTY}</td>
                </tr>
                <tr>
                  <td className="ddp-parcel-label">Weight</td>
                  <td className="ddp-parcel-value">{parcel.weight != null ? `${parcel.weight} kg` : EMPTY}</td>
                </tr>
                <tr>
                  <td className="ddp-parcel-label">Dimensions</td>
                  <td className="ddp-parcel-value">{parcel.size ? sizeLabel(parcel.size) : EMPTY}</td>
                </tr>
                <tr>
                  <td className="ddp-parcel-label">Handling note</td>
                  <td className="ddp-parcel-value">{EMPTY}</td>
                </tr>
                <tr>
                  <td className="ddp-parcel-label">Courier</td>
                  <td className="ddp-parcel-value">{EMPTY}</td>
                </tr>
                <tr>
                  <td className="ddp-parcel-label">Source</td>
                  <td className="ddp-parcel-value">Customer</td>
                </tr>
              </tbody>
            </table>
          </section>

          {/* Delivery history card */}
          <section className="ddp-card" aria-labelledby="ddp-history-title">
            <h2 className="ddp-card-title" id="ddp-history-title">Delivery history</h2>
            <p className="ddp-history-subtitle">Every status change, including earlier attempts.</p>
            {historyAsc.length === 0 ? (
              <p className="ddp-history-empty">No status events have been recorded.</p>
            ) : (
              <div className="ddp-history-table-wrap">
                <table className="ddp-history-table">
                  <tbody>
                    {historyAsc.map((evt) => {
                      const actor = getActor(evt.newStatus)
                      return (
                        <tr key={evt.id} className="ddp-history-row">
                          <td className="ddp-history-time">{formatDateTime(evt.changedAt)}</td>
                          <td className="ddp-history-badge-cell">
                            <StatusBadge status={evt.newStatus} size="sm" />
                          </td>
                          <td className="ddp-history-note">{evt.note || EMPTY}</td>
                          <td className="ddp-history-actor">Attempt 1 · by {actor}</td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </div>

        {/* ── RIGHT COLUMN ── */}
        <div className="ddp-right">

          {/* Progress stepper card */}
          <section className="ddp-card" aria-labelledby="ddp-progress-title">
            <h2 className="ddp-card-title" id="ddp-progress-title">Progress</h2>
            {isTerminal ? (
              <div className="ddp-terminal-status">
                <StatusBadge status={parcel.status} size="md" />
                <p className="ddp-terminal-note">
                  {parcel.status === 'CANCELLED' ? 'This delivery was cancelled.' : 'This delivery attempt failed.'}
                </p>
              </div>
            ) : (
              <ol className="ddp-stepper" aria-label="Delivery progress">
                {PROGRESS_STEPS.map((step, idx) => {
                  const isDone = idx < currentStepIndex
                  const isCurrent = idx === currentStepIndex
                  const isFuture = idx > currentStepIndex
                  const histEvt = historyByStatus[step]
                  const cfg = STATUS_CONFIG[step]
                  return (
                    <li
                      key={step}
                      className={`ddp-step${isDone ? ' ddp-step-done' : ''}${isCurrent ? ' ddp-step-current' : ''}${isFuture ? ' ddp-step-future' : ''}`}
                    >
                      <div className="ddp-step-dot-col">
                        <div className="ddp-step-dot">
                          {isDone && <CheckIcon />}
                          {isCurrent && <div className="ddp-step-dot-inner" />}
                        </div>
                        {idx < PROGRESS_STEPS.length - 1 && (
                          <div className={`ddp-step-line${isDone || isCurrent ? ' ddp-step-line-done' : ''}`} />
                        )}
                      </div>
                      <div className="ddp-step-info">
                        <span className="ddp-step-label">
                          {cfg?.label ?? humanize(step)}
                          {isCurrent && <span className="ddp-step-current-tag"> (current)</span>}
                        </span>
                        {histEvt && (
                          <span className="ddp-step-time">
                            {formatDateTime(histEvt.changedAt)}
                            {histEvt.note ? ` · ${histEvt.note}` : ''}
                          </span>
                        )}
                      </div>
                    </li>
                  )
                })}
              </ol>
            )}
          </section>

          {/* Fee summary card */}
          <section className="ddp-card ddp-fee-card" aria-labelledby="ddp-fee-title">
            <h2 className="ddp-card-title" id="ddp-fee-title">Fee summary</h2>
            <table className="ddp-fee-table">
              <tbody>
                <tr>
                  <td className="ddp-fee-label">Base fee (1 kg)</td>
                  <td className="ddp-fee-value">
                    LKR {parcel.charge != null ? Number(parcel.charge).toLocaleString('en-LK') : EMPTY}
                  </td>
                </tr>
                <tr>
                  <td className="ddp-fee-label">Additional weight</td>
                  <td className="ddp-fee-value">LKR 0</td>
                </tr>
                <tr>
                  <td className="ddp-fee-label">Size surcharge ({parcel.size ? sizeLabel(parcel.size) : '—'})</td>
                  <td className="ddp-fee-value">LKR 0</td>
                </tr>
              </tbody>
              <tfoot>
                <tr className="ddp-fee-total-row">
                  <td className="ddp-fee-total-label">Estimated delivery fee</td>
                  <td className="ddp-fee-total-value">
                    LKR {parcel.charge != null ? Number(parcel.charge).toLocaleString('en-LK') : EMPTY}
                  </td>
                </tr>
              </tfoot>
            </table>
            <p className="ddp-fee-note">
              Illustrative demo pricing, rate version v3. Payment is outside this prototype's scope.
            </p>
          </section>

        </div>
      </div>
    </div>
  )
}
