import { useEffect, useRef, useState } from 'react'
import DeliveryPhoto from '../../components/DeliveryPhoto'
import LoadingSpinner from '../../components/LoadingSpinner'
import { dispatchService } from '../../services/dispatchService'
import { FAILURE_REASONS } from '../../utils/courier/courierValidation'
import { formatDateTime, statusLabel } from './dispatchFormat'

const reasonLabel = (value) => FAILURE_REASONS.find((reason) => reason.value === value)?.label || statusLabel(value)

/** One delivery for the dispatcher: parcel summary plus everything the courier recorded, photos included. */
export default function DeliveryDetailDialog({ parcelId, onClose }) {
  const [state, setState] = useState({ loading: true })
  const closeButton = useRef(null)

  useEffect(() => {
    let current = true
    Promise.allSettled([dispatchService.getParcel(parcelId), dispatchService.getCourierUpdates(parcelId)]).then(([parcel, updates]) => {
      if (!current) return
      if (parcel.status === 'rejected') {
        setState({ error: parcel.reason?.message || 'Unable to load delivery details.' })
        return
      }
      setState({
        detail: parcel.value,
        updates: updates.status === 'fulfilled' && Array.isArray(updates.value) ? updates.value : [],
        updatesError: updates.status === 'rejected' ? updates.reason?.message || 'Courier updates could not be loaded.' : '',
      })
    })
    return () => { current = false }
  }, [parcelId])

  useEffect(() => {
    closeButton.current?.focus()
    const onKey = (event) => { if (event.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  const parcel = state.detail?.parcel
  return (
    <div className="dispatch-dialog-backdrop" onClick={(event) => { if (event.target === event.currentTarget) onClose() }}>
      <section className="dispatch-dialog dispatch-detail card" role="dialog" aria-modal="true" aria-labelledby="delivery-detail-title">
        <button ref={closeButton} className="dispatch-dialog-close" aria-label="Close delivery details" onClick={onClose}>×</button>
        {state.loading ? (
          <>
            <h2 id="delivery-detail-title" className="visually-hidden">Delivery details</h2>
            <LoadingSpinner label="Loading delivery details" />
          </>
        ) : state.error ? (
          <>
            <h2 id="delivery-detail-title">Unable to load delivery details</h2>
            <p className="dispatch-error" role="alert">{state.error}</p>
          </>
        ) : (
          <>
            <h2 id="delivery-detail-title">{parcel?.trackingNumber || 'Delivery details'}</h2>
            {parcel && <p>{parcel.pickupAddress} → {parcel.deliveryAddress}</p>}
            {parcel && <p>Status: <strong>{statusLabel(parcel.status)}</strong></p>}
            <h3>Courier updates</h3>
            {state.updatesError && <p className="dispatch-error" role="alert">{state.updatesError}</p>}
            {!state.updatesError && state.updates.length === 0 ? (
              <p className="dispatch-empty">The courier has not recorded any updates yet.</p>
            ) : (
              <ol className="dispatch-timeline dispatch-updates">
                {state.updates.map((update) => (
                  <li key={update.id}>
                    <strong>{statusLabel(update.status)}</strong>
                    {update.courierName && <> · {update.courierName}</>}
                    <time dateTime={update.recordedAt}>
                      {formatDateTime(update.recordedAt)}{update.attemptNo > 1 && ` · attempt ${update.attemptNo}`}
                    </time>
                    {update.recipientName && <p>Received by <strong>{update.recipientName}</strong></p>}
                    {update.failureReason && <p>Reason: {reasonLabel(update.failureReason)}</p>}
                    {update.note && <p className="dispatch-update-note">{update.note}</p>}
                    <DeliveryPhoto url={update.photoUrl} alt={`Courier photo for ${statusLabel(update.status).toLowerCase()}`} />
                  </li>
                ))}
              </ol>
            )}
          </>
        )}
      </section>
    </div>
  )
}
