import { useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import Alert from '../../components/Alert'
import EmptyState from '../../components/EmptyState'
import LoadingSpinner from '../../components/LoadingSpinner'
import TrackingSearch from '../../components/TrackingSearch'
import { useParcelLiveStatus } from '../../hooks/useParcelLiveStatus'
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

/**
 * Maps the Parcel Service public tracking response
 * ({ trackingNumber, status, createdAt, deliveryCity, trackingHistory: [{ status, timestamp }] })
 * to the shape this page renders and caches.
 */
function fromTrackingResponse(response) {
  return {
    trackingNumber: response.trackingNumber,
    status: response.status,
    deliveryCity: response.deliveryCity || null,
    history: Array.isArray(response.trackingHistory)
      ? response.trackingHistory.map((entry) => ({ status: entry.status, timestamp: entry.timestamp }))
      : [],
  }
}

/** Server history first, then any statuses that arrived on the live stream since. */
function mergeHistory(base, events, liveStatus, liveUpdatedAt) {
  const merged = Array.isArray(base) ? [...base] : []
  for (const ev of events || []) {
    if (ev.status && !merged.some((h) => h.status === ev.status)) {
      merged.push({ status: ev.status, timestamp: ev.occurredAt || ev.timestamp || null })
    }
  }
  if (liveStatus && liveStatus !== 'WAITING_FOR_UPDATES'
      && !merged.some((h) => h.status === liveStatus)) {
    merged.push({ status: liveStatus, timestamp: liveUpdatedAt || null })
  }
  return merged
}

function TrackingResult({ parcel, onLiveUpdate }) {
  const {
    status: hookStatus, updatedAt: liveUpdatedAt, isConnected, events, lastEvent,
  } = useParcelLiveStatus(parcel.trackingNumber, parcel.status)

  // The hook is seeded with the first status it was given (possibly a cached one)
  // and keeps it until the stream sends something, so only trust it after an event.
  const liveStatus = lastEvent?.status ? hookStatus : null
  const currentStatus = liveStatus || parcel.status
  const history = useMemo(
    () => mergeHistory(parcel.history, events, liveStatus, liveUpdatedAt),
    [parcel.history, events, liveStatus, liveUpdatedAt]
  )

  useEffect(() => {
    if (liveStatus && liveStatus !== 'WAITING_FOR_UPDATES') {
      onLiveUpdate?.()
    }
  }, [liveStatus, onLiveUpdate])

  useEffect(() => {
    if (currentStatus && currentStatus !== 'WAITING_FOR_UPDATES') {
      try {
        sessionStorage.setItem(
          `swiftdrop_track_${parcel.trackingNumber}`,
          JSON.stringify({
            trackingNumber: parcel.trackingNumber,
            status: currentStatus,
            deliveryCity: parcel.deliveryCity || null,
            history,
          })
        )
      } catch {
        // Ignore storage errors
      }
    }
  }, [parcel.trackingNumber, parcel.deliveryCity, currentStatus, history])

  return (
    <section className="card tracking-result" aria-labelledby="tracking-result-title">
      <header className="tracking-result-head">
        <div>
          <p className="hero-card-eyebrow">Tracking number</p>
          <h2 id="tracking-result-title" className="card-title">{parcel.trackingNumber}</h2>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          {isConnected && (
            <span
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '4px',
                fontSize: '11px',
                color: '#16a34a',
                fontWeight: 600,
              }}
            >
              <span
                style={{
                  width: '6px',
                  height: '6px',
                  borderRadius: '50%',
                  backgroundColor: '#16a34a',
                  display: 'inline-block',
                }}
              />
              LIVE
            </span>
          )}
          <span className="pill pill-info">{formatStatus(currentStatus)}</span>
        </div>
      </header>
      {parcel.deliveryCity && (
        <p className="tracking-destination">Delivering to <strong>{parcel.deliveryCity}</strong></p>
      )}
      {history.length === 0 ? (
        <EmptyState title="No updates yet" description="Status updates will appear here." />
      ) : (
        <ol className="timeline">
          {history.map((entry, index) => (
            <li key={`${entry.status}-${entry.timestamp || entry.occurredAt}-${index}`}>
              <p className="timeline-status">{formatStatus(entry.status)}</p>
              <p className="timeline-time">{formatDateTime(entry.timestamp || entry.occurredAt)}</p>
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
  const [result, setResult] = useState(() => {
    if (valid) {
      try {
        const cached = sessionStorage.getItem(`swiftdrop_track_${trackingNumber}`)
        if (cached) {
          const parsed = JSON.parse(cached)
          return { key: trackingNumber, parcel: parsed, error: null }
        }
      } catch {
        // Ignore
      }
    }
    return { key: null, parcel: null, error: null }
  })
  const [hasLiveUpdate, setHasLiveUpdate] = useState(() => {
    if (valid) {
      try {
        return !!sessionStorage.getItem(`swiftdrop_track_${trackingNumber}`)
      } catch {
        return false
      }
    }
    return false
  })

  useEffect(() => {
    let hasCached = false
    try {
      hasCached = !!sessionStorage.getItem(`swiftdrop_track_${trackingNumber}`)
    } catch {
      hasCached = false
    }
    setHasLiveUpdate(hasCached)
  }, [trackingNumber])

  useEffect(() => {
    if (!valid) return undefined
    let cachedParcel = null
    try {
      const cached = sessionStorage.getItem(`swiftdrop_track_${trackingNumber}`)
      if (cached) {
        cachedParcel = JSON.parse(cached)
        setResult({ key: trackingNumber, parcel: cachedParcel, error: null })
        setHasLiveUpdate(true)
      }
    } catch {
      // Ignore
    }

    const controller = new AbortController()
    trackParcel(trackingNumber, { signal: controller.signal })
      .then((response) => {
        // The server is authoritative: a fresh response replaces the cached view.
        setResult({ key: trackingNumber, parcel: fromTrackingResponse(response), error: null })
      })
      .catch((error) => {
        if (error?.name === 'AbortError') return
        if (!cachedParcel) {
          setResult({ key: trackingNumber, parcel: null, error })
        }
      })
    return () => controller.abort()
  }, [trackingNumber, valid])

  const current = result.key === trackingNumber ? result : null
  const loading = valid && current === null

  return (
    <div className="section track-parcel-section">
      <div className="container track-parcel-container">
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
        {current?.error && !hasLiveUpdate && (
          <Alert variant={current.error.kind === 'NOT_IMPLEMENTED' ? 'info' : 'error'}
            title={current.error.kind === 'NOT_IMPLEMENTED' ? 'Tracking coming soon' : undefined}>
            {describeError(current.error, {
              notImplemented: NOT_IMPLEMENTED_MESSAGE,
              byStatus: { 404: `We could not find a parcel with tracking number ${trackingNumber}.` },
            })}
          </Alert>
        )}
        {current?.parcel ? (
          <TrackingResult parcel={current.parcel} onLiveUpdate={() => setHasLiveUpdate(true)} />
        ) : current?.error?.kind === 'NOT_IMPLEMENTED' && valid ? (
          <div style={{ marginTop: '24px' }}>
            <TrackingResult parcel={{ trackingNumber, status: 'WAITING_FOR_UPDATES', history: [] }} onLiveUpdate={() => setHasLiveUpdate(true)} />
          </div>
        ) : null}
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
