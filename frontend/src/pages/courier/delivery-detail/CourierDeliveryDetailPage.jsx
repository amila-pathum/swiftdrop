import { useParams } from 'react-router-dom'
import PageHeader from '../../../components/PageHeader'
import Button, { ButtonLink } from '../../../components/Button'
import Alert from '../../../components/Alert'
import LoadingSpinner from '../../../components/LoadingSpinner'
import DeliveryStatusBadge from '../../../components/courier/DeliveryStatusBadge'
import ProofPhoto from '../../../components/courier/ProofPhoto'
import { useCourierDelivery } from '../../../hooks/courier/useCourierDeliveries'
import { activeDelivery, address, courierError, dateLabel, STATUS } from '../../../utils/courier/deliveryStatus'
import DeliveryUpdateForm from './components/DeliveryUpdateForm'
import '../../../components/courier/courier.css'
import './courierDeliveryDetail.css'

export default function CourierDeliveryDetailPage() {
  const { parcelId } = useParams()
  const resource = useCourierDelivery(parcelId)
  const delivery = resource.data
  return <section className="courier-page"><ButtonLink variant="ghost" to="/courier/deliveries">Back to deliveries</ButtonLink><PageHeader title={delivery?.parcel.trackingNumber || 'Delivery details'} description="Review the parcel, record its progress and capture proof of delivery." />
    {resource.loading && <div role="status" className="courier-loading"><LoadingSpinner /><span>Loading delivery...</span></div>}
    {resource.error && <Alert variant="error"><p>{courierError(resource.error)}</p><Button variant="outline" onClick={resource.reload}>Try again</Button></Alert>}
    {delivery && <><section className="courier-panel"><header className="courier-panel-heading"><h2>Parcel details</h2><DeliveryStatusBadge status={delivery.parcel.status} /></header><div className="courier-panel-body"><p className="courier-help">Scheduled {dateLabel(delivery.assignment.deliveryDate)}</p><ol className="courier-progress" aria-label="Delivery progress">{['CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED'].map((status) => <li key={status} aria-current={delivery.parcel.status === status ? 'step' : undefined}>{STATUS[status].label}</li>)}</ol><div className="courier-detail-addresses"><div><h3>Collect from</h3><p>{address(delivery.parcel, 'pickup')}</p></div><div><h3>Deliver to</h3><p>{address(delivery.parcel, 'delivery')}</p><p>{delivery.parcel.receiverName}</p>{delivery.parcel.receiverPhone && <a href={`tel:${delivery.parcel.receiverPhone.replace(/[^+0-9]/g, '')}`}>{delivery.parcel.receiverPhone}</a>}</div></div></div></section>
    {activeDelivery(delivery) ? <DeliveryUpdateForm key={`${parcelId}:${delivery.parcel.status}`} parcel={delivery.parcel} onConfirmed={resource.reload} /> : <Alert variant="info">This delivery is read-only because it is completed, cancelled or no longer assigned to you.</Alert>}
    <section className="courier-panel"><header className="courier-panel-heading"><h2>Recorded locations and proof</h2></header><div className="courier-panel-body">{delivery.evidenceUnavailable && <Alert variant="warning">Some tracking or proof details could not be loaded.</Alert>}<ol className="courier-timeline">{(Array.isArray(delivery.tracking) ? delivery.tracking : []).map((point) => <li key={point.id}><strong>{STATUS[point.status]?.label || point.status || point.type}</strong><p>{point.locationDescription || 'Location recorded'}</p><time>{dateLabel(point.recordedAt || point.capturedAt, { dateStyle: 'medium', timeStyle: 'short' })}</time>{point.latitude != null && point.longitude != null && Number.isFinite(Number(point.latitude)) && Number.isFinite(Number(point.longitude)) && <p><a href={`https://www.google.com/maps?q=${Number(point.latitude)},${Number(point.longitude)}`} target="_blank" rel="noreferrer">View recorded location</a></p>}</li>)}</ol>{(Array.isArray(delivery.proofs) ? delivery.proofs : []).map((proof) => <article key={proof.id} className="courier-proof"><h3>{STATUS[proof.outcome]?.label || proof.outcome}</h3><p>{proof.recipientName || proof.failureReason}</p>{proof.note && <p>{proof.note}</p>}<p className="courier-help">{dateLabel(proof.recordedAt, { dateStyle: 'medium', timeStyle: 'short' })}</p>{proof.photoUrl && <ProofPhoto parcelId={parcelId} proofId={proof.id} />}</article>)}{!delivery.tracking?.length && !delivery.proofs?.length && <p className="courier-help">No confirmed locations or proof have been recorded yet.</p>}</div></section></>}
  </section>
}
