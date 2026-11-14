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

function Detail({ label, children }) {
  return (
    <div className="parcel-detail-item">
      <dt>{label}</dt>
      <dd>{children ?? EMPTY}</dd>
    </div>
  )
}

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

  return (
    <div className="delivery-page-container parcel-details-page">
      <nav className="delivery-breadcrumb" aria-label="Breadcrumb">
        <Link className="delivery-breadcrumb-muted" to="/customer/deliveries">My Deliveries</Link>
        <span className="delivery-breadcrumb-sep" aria-hidden="true">&rsaquo;</span>
        <span className="delivery-breadcrumb-active">{parcel.trackingNumber}</span>
      </nav>

      <div className="delivery-header-row">
        <div>
          <h1 className="delivery-page-title">Delivery details</h1>
          <div className="parcel-details-tracking-row">
            <p className="parcel-details-tracking">{parcel.trackingNumber}</p>
            <CopyTrackingNumberButton trackingNumber={parcel.trackingNumber} />
          </div>
        </div>
        <span className="parcel-details-status">{humanize(parcel.status)}</span>
      </div>

      <div className="parcel-details-layout">
        <section className="delivery-table-card parcel-details-card" aria-labelledby="parcel-information-title">
          <h2 id="parcel-information-title">Parcel information</h2>
          <dl className="parcel-details-grid">
            <Detail label="Receiver">{parcel.receiverName}</Detail>
            <Detail label="Receiver phone">{parcel.receiverPhone}</Detail>
            <Detail label="Pickup address">{parcel.pickupAddress}</Detail>
            <Detail label="Delivery address">{parcel.deliveryAddress}</Detail>
            <Detail label="Weight">{parcel.weight != null ? `${parcel.weight} kg` : EMPTY}</Detail>
            <Detail label="Size">{parcel.size}</Detail>
            <Detail label="Parcel type">{humanize(parcel.parcelType)}</Detail>
            <Detail label="Delivery fee">{parcel.charge != null ? `LKR ${Number(parcel.charge).toFixed(2)}` : EMPTY}</Detail>
            <Detail label="Created">{formatDateTime(parcel.createdAt)}</Detail>
            <Detail label="Last updated">{formatDateTime(parcel.updatedAt)}</Detail>
          </dl>
        </section>

        <section className="delivery-table-card parcel-details-card" aria-labelledby="parcel-history-title">
          <h2 id="parcel-history-title">Status history</h2>
          {history.length === 0 ? (
            <p className="parcel-history-empty">No status events have been recorded.</p>
          ) : (
            <ol className="timeline parcel-history-timeline">
              {history.map((event) => (
                <li key={event.id}>
                  <div className="timeline-status">{humanize(event.newStatus)}</div>
                  <time className="timeline-time" dateTime={event.changedAt}>{formatDateTime(event.changedAt)}</time>
                  {event.note && <div className="timeline-note">{event.note}</div>}
                </li>
              ))}
            </ol>
          )}
        </section>
      </div>
    </div>
  )
}
