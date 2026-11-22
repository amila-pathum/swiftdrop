import { ButtonLink } from '../Button'
import { STATUS, activeDelivery, address, dateLabel, terminal } from '../../utils/courier/deliveryStatus'
import CourierIcon from './CourierIcon'
import DeliveryStatusBadge from './DeliveryStatusBadge'

export default function DeliveryCard({ delivery }) {
  const { parcel, assignment } = delivery
  const active = activeDelivery(delivery)
  const archived = !assignment.active && !terminal(parcel.status)
  const meta = [assignment.deliveryDate ? `Scheduled ${dateLabel(assignment.deliveryDate)}` : 'Not scheduled']
  if (parcel.weight !== null && parcel.weight !== undefined) meta.push(`${parcel.weight} kg`)
  if (parcel.size) meta.push(parcel.size.charAt(0) + parcel.size.slice(1).toLowerCase())
  return (
    <article className="courier-delivery-card">
      <header className="courier-card-heading"><h3>{parcel.trackingNumber || parcel.id}</h3><DeliveryStatusBadge status={parcel.status} archived={archived} /></header>
      <p className="courier-card-meta">{meta.join(' · ')}</p>
      <div className="courier-address"><CourierIcon name="pin" /><div><span>Deliver to</span><p>{address(parcel, 'delivery')}</p></div></div>
      <footer className="courier-card-footer"><p>{active ? 'Next: ' : ''}<strong>{archived ? 'No longer assigned' : STATUS[parcel.status]?.action || 'View details'}</strong></p>
        <ButtonLink variant={active ? 'primary' : 'outline'} to={`/courier/deliveries/${encodeURIComponent(parcel.id)}`} aria-label={`Open delivery ${parcel.trackingNumber || parcel.id}`}>Open Delivery</ButtonLink>
      </footer>
    </article>
  )
}
