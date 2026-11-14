import EmptyState from '../EmptyState'
import { deliveryKey } from '../../utils/courier/deliveryStatus'
import DeliveryCard from './DeliveryCard'

export default function DeliveryGrid({ deliveries, emptyTitle = 'No deliveries to show', emptyDescription = 'New assignments will appear here once your dispatcher assigns them.' }) {
  return deliveries.length ? <div className="courier-delivery-grid">{deliveries.map((delivery) => <DeliveryCard key={deliveryKey(delivery)} delivery={delivery} />)}</div>
    : <EmptyState title={emptyTitle} description={emptyDescription} />
}
