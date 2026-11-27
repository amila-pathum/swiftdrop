import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import Button from '../../../components/Button'
import Alert from '../../../components/Alert'
import LoadingSpinner from '../../../components/LoadingSpinner'
import CourierIcon from '../../../components/courier/CourierIcon'
import DeliveryStatusBadge from '../../../components/courier/DeliveryStatusBadge'
import DeliveryPhoto from '../../../components/DeliveryPhoto'
import { useCourierDelivery } from '../../../hooks/courier/useCourierDeliveries'
import { activeDelivery, address, courierError, dateLabel, STATUS } from '../../../utils/courier/deliveryStatus'
import DeliveryUpdateForm from './components/DeliveryUpdateForm'
import '../../../components/courier/courier.css'
import './courierDeliveryDetail.css'

const PROGRESS = ['CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED']
const ACTIONS = { CREATED: 'Mark Picked Up', PICKED_UP: 'Mark In Transit', IN_TRANSIT: 'Mark Out for Delivery', OUT_FOR_DELIVERY: 'Deliver or report failure' }
// Shorter badge text where the History column is narrow; other places keep the full label.
const HISTORY_LABELS = { OUT_FOR_DELIVERY: 'Out' }
const when = (value) => value ? dateLabel(value, { dateStyle: 'medium', timeStyle: 'short' }) : 'Time not recorded'
const number = (value) => new Intl.NumberFormat('en-GB', { maximumFractionDigits: 3 }).format(Number(value))
const roleLabel = (role) => role ? role.charAt(0).toUpperCase() + role.slice(1).toLowerCase() : 'Role not recorded'

function Panel({ title, children, className = '' }) {
  return <section className={`courier-panel detail-panel ${className}`} aria-label={title}>
    <header className="courier-panel-heading"><h2>{title}</h2></header>
    <div className="courier-panel-body">{children}</div>
  </section>
}

function Timeline({ status, history }) {
  const stopped = ['FAILED', 'CANCELLED'].includes(status)
  const stages = stopped ? [...PROGRESS.slice(0, -1), status] : PROGRESS
  const current = stages.indexOf(status)
  return <ol className="detail-timeline" aria-label="Delivery progress">
    {stages.map((stage, index) => {
      const entry = history.find((item) => item.status === stage)
      const complete = stopped ? Boolean(entry) && stage !== status : current >= 0 && index < current
      const isCurrent = stage === status
      return <li key={stage} className={complete || isCurrent ? 'is-reached' : ''} aria-current={isCurrent ? 'step' : undefined}>
        <span className="detail-step-marker" aria-hidden="true">{complete || isCurrent
          ? <svg viewBox="0 0 20 20" fill="none"><path d="m5 10 3 3 7-7" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" /></svg>
          : <span />}</span>
        <div><span className="detail-step-name">{STATUS[stage]?.label || stage}</span>{isCurrent && <span className="detail-muted"> (current)</span>}
          {(complete || isCurrent) && <p className="detail-step-meta">{when(entry?.recordedAt)}{entry?.locationDescription && ` \u00b7 ${entry.locationDescription}`}</p>}
        </div>
      </li>
    })}
  </ol>
}

export default function CourierDeliveryDetailPage() {
  const { parcelId } = useParams()
  const resource = useCourierDelivery(parcelId)
  const [editingParcel, setEditingParcel] = useState(null)
  const delivery = resource.data
  const parcel = delivery?.parcel
  const history = [...(Array.isArray(parcel?.history) && parcel.history.length ? parcel.history : Array.isArray(delivery?.tracking) ? delivery.tracking : [])]
    .sort((a, b) => new Date(b.recordedAt) - new Date(a.recordedAt))
  // Parcel-service history carries no photos; take them from this courier's own confirmed updates.
  const photos = new Map((Array.isArray(delivery?.tracking) ? delivery.tracking : []).filter((point) => point.photoUrl)
    .map((point) => [`${point.status}:${point.attemptNo}`, point.photoUrl]))
  const photoFor = (point) => point.photoUrl || photos.get(`${point.status}:${point.attemptNo}`)
  const dimensions = [parcel?.lengthCm, parcel?.widthCm, parcel?.heightCm]
  const dimensionText = dimensions.every((value) => value != null && Number.isFinite(Number(value)) && Number(value) > 0)
    ? `${dimensions.map(number).join(' \u00d7 ')} cm` : null
  const size = parcel?.size ? parcel.size.charAt(0).toUpperCase() + parcel.size.slice(1).toLowerCase() : null
  const phone = parcel?.receiverPhone?.replace(/[^+0-9]/g, '')
  const trackingNumber = parcel?.trackingNumber || 'Delivery details'

  return <section className="courier-page courier-delivery-detail">
    <header className="detail-page-header">
      <nav className="detail-breadcrumb" aria-label="Breadcrumb"><Link to="/courier/deliveries">Deliveries</Link><span aria-hidden="true">{'\u203a'}</span><span aria-current="page">{trackingNumber}</span></nav>
      <h1>{trackingNumber}</h1>
      {parcel && <DeliveryStatusBadge status={parcel.status} />}
    </header>
    {resource.loading && <div role="status" className="courier-loading"><LoadingSpinner /><span>Loading delivery...</span></div>}
    {resource.error && <Alert variant="error"><p>{courierError(resource.error)}</p><Button variant="outline" onClick={resource.reload}>Try again</Button></Alert>}
    {delivery && <>
      <section className="courier-panel detail-recipient" aria-label="Deliver to">
        <span className="detail-label">Deliver to</span>
        <h2>{parcel.receiverName || 'Recipient not provided'}</h2>
        <p>{address(parcel, 'delivery')}</p>
        {phone ? <a className="detail-call" href={`tel:${phone}`}><CourierIcon name="phone" size={16} />Call {parcel.receiverPhone}</a>
          : <p className="detail-call detail-muted">Phone not provided</p>}
      </section>
      <div className="detail-contact-grid">
        <Panel title="Pickup"><p>{parcel.pickupContactName || 'Pickup contact not provided'}</p><p>{address(parcel, 'pickup')}</p>
          <p>{parcel.pickupContactPhone || 'Phone not provided'}</p></Panel>
        <Panel title="Drop-off"><p>{parcel.receiverName || 'Recipient not provided'}</p><p>{address(parcel, 'delivery')}</p><p>{parcel.receiverPhone || 'Phone not provided'}</p></Panel>
      </div>
      <Panel title="Parcel"><dl className="detail-parcel-facts">
        <dt>Contents</dt><dd>{parcel.contentsDescription || 'Not provided'}</dd>
        <dt>Weight</dt><dd>{parcel.weight != null ? `${number(parcel.weight)} kg` : 'Not provided'}</dd>
        <dt>Size</dt><dd>{dimensionText ? `${dimensionText}${size ? ` (${size})` : ''}` : size || 'Not provided'}</dd>
        <dt>Scheduled</dt><dd>{dateLabel(delivery.assignment.deliveryDate)}</dd>
      </dl></Panel>
      <Panel title="Timeline"><Timeline status={parcel.status} history={history} /></Panel>
      <Panel title="History">{history.length ? <ol className="detail-history" aria-label="Delivery history">
        {history.map((point) => <li key={point.id}>
          <time dateTime={point.recordedAt}>{when(point.recordedAt)}</time>
          <DeliveryStatusBadge status={point.status} label={HISTORY_LABELS[point.status]} />
          <span className="detail-history-location">{point.locationDescription || 'Location not recorded'}</span>
          <span className="detail-history-actor">{point.attemptNo != null ? `Attempt ${point.attemptNo}` : 'Attempt not recorded'} {'\u00b7'} by {roleLabel(point.actorRole)}</span>
          {photoFor(point) && <DeliveryPhoto url={photoFor(point)} alt={`Photo for ${STATUS[point.status]?.label || point.status}`} />}
        </li>)}
      </ol> : <p className="courier-help">No confirmed history is available yet.</p>}</Panel>
      {delivery.evidenceUnavailable && <Alert variant="warning">Some tracking or proof details could not be loaded.</Alert>}
      {delivery.proofs?.length > 0 && <Panel title="Delivery evidence">{delivery.proofs.map((proof) => <article key={proof.id} className="courier-proof">
        <h3>{STATUS[proof.outcome]?.label || proof.outcome}</h3><p>{proof.recipientName || proof.failureReason}</p>{proof.note && <p>{proof.note}</p>}
        <p className="courier-help">{when(proof.recordedAt)}</p><DeliveryPhoto url={proof.photoUrl} alt="Delivery evidence photo" />
      </article>)}</Panel>}
      {activeDelivery(delivery) && ACTIONS[parcel.status] ? <div className="courier-panel detail-actions">
        {editingParcel === `${parcelId}:${parcel.status}` ? <>
          <Button variant="ghost" onClick={() => setEditingParcel(null)}>Close update</Button>
          <DeliveryUpdateForm key={`${parcelId}:${parcel.status}`} parcel={parcel} onConfirmed={() => { setEditingParcel(null); resource.reload() }} />
        </> : <Button fullWidth onClick={() => setEditingParcel(`${parcelId}:${parcel.status}`)}>{ACTIONS[parcel.status]}</Button>}
      </div> : <Alert variant="info">This delivery is read-only because it is completed, cancelled or no longer assigned to you.</Alert>}
    </>}
  </section>
}
