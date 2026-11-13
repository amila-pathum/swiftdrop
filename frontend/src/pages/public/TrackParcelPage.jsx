import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import Alert from '../../components/Alert'
import EmptyState from '../../components/EmptyState'
import LoadingSpinner from '../../components/LoadingSpinner'
import TrackingSearch from '../../components/TrackingSearch'
import { describeError } from '../../services/errorMessages'
import {
  normalizeTrackingNumber,
  trackParcel,
  TRACKING_NUMBER_PATTERN,
} from '../../services/parcelService'

const NOT_IMPLEMENTED_MESSAGE = 'Online parcel tracking is not available yet. It will be enabled '
  + 'once the Parcel Service publishes its tracking endpoint. Please contact the sender for an update.'

function formatStatus(value) {
  return typeof value === 'string'
    ? value.charAt(0) + value.slice(1).toLowerCase().replace(/_/g, ' ')
    : '—'
}

function formatDateTime(value) {
  const date = new Date(value)
  return value && !Number.isNaN(date.getTime())
    ? date.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
    : '—'
}

function TrackingResult({ parcel }) {
  const history = Array.isArray(parcel.history) ? parcel.history : []
  return (
    <section className="card tracking-result" aria-labelledby="tracking-result-title">
      <header className="tracking-result-head">
        <div>
          <p className="hero-card-eyebrow">Tracking number</p>
          <h2 id="tracking-result-title" className="card-title">{parcel.trackingNumber}</h2>
        </div>
        <span className="pill pill-info">{formatStatus(parcel.status)}</span>
      </header>
      {history.length === 0 ? (
        <EmptyState title="No updates yet" description="Status updates will appear here." />
      ) : (
        <ol className="timeline">
          {history.map((entry, index) => (
            <li key={`${entry.status}-${entry.occurredAt}-${index}`}>
              <p className="timeline-status">{formatStatus(entry.status)}</p>
              <p className="timeline-time">{formatDateTime(entry.occurredAt)}</p>
              {entry.note && <p className="timeline-note">{entry.note}</p>}
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}

/** /track and /track/:trackingNumber. Public. */
export default function TrackParcelPage() {
  const params = useParams()
  const trackingNumber = normalizeTrackingNumber(params.trackingNumber)
  const valid = TRACKING_NUMBER_PATTERN.test(trackingNumber)
  const [result, setResult] = useState({ key: null, parcel: null, error: null })

  useEffect(() => {
    if (!valid) return undefined
    const controller = new AbortController()
    trackParcel(trackingNumber, { signal: controller.signal })
      .then((parcel) => setResult({ key: trackingNumber, parcel, error: null }))
      .catch((error) => {
        if (error?.name === 'AbortError') return
        setResult({ key: trackingNumber, parcel: null, error })
      })
    return () => controller.abort()
  }, [trackingNumber, valid])

  const current = result.key === trackingNumber ? result : null
  const loading = valid && current === null

  return (
    <div className="section">
      <div className="container container-narrow">
        <h1 className="section-title">Track a parcel</h1>
        <p className="section-lead">Enter the tracking number from your booking confirmation.</p>
        <div className="track-page-search">
          <TrackingSearch key={trackingNumber} id="page-tracking-number" initialValue={trackingNumber} size="md" />
        </div>

        {trackingNumber !== '' && !valid && (
          <Alert variant="error">That does not look like a SwiftDrop tracking number.</Alert>
        )}
        {loading && (
          <div className="card status-card status-card-loading">
            <LoadingSpinner />
            <p role="status">Looking up {trackingNumber}…</p>
          </div>
        )}
        {current?.error && (
          <Alert variant={current.error.kind === 'NOT_IMPLEMENTED' ? 'info' : 'error'}
            title={current.error.kind === 'NOT_IMPLEMENTED' ? 'Tracking coming soon' : undefined}>
            {describeError(current.error, {
              notImplemented: NOT_IMPLEMENTED_MESSAGE,
              byStatus: { 404: `We could not find a parcel with tracking number ${trackingNumber}.` },
            })}
          </Alert>
        )}
        {current?.parcel && <TrackingResult parcel={current.parcel} />}
        {trackingNumber === '' && (
          <EmptyState
            title="No tracking number entered"
            description="Your tracking number is in the booking confirmation from SwiftDrop or the sender."
          />
        )}
      </div>
    </div>
  )
}
