import { STATUS } from '../../utils/courier/deliveryStatus'
import CourierIcon from './CourierIcon'

/** `label` replaces the status text, for example a shorter word where space is tight. */
export default function DeliveryStatusBadge({ status, archived = false, label }) {
  const info = archived ? { label: 'Reassigned', tone: 'neutral' } : STATUS[status] || { label: 'Status unavailable', tone: 'neutral' }
  return <span className={`courier-status-badge courier-status-${info.tone}`}><CourierIcon size={13} name={status === 'DELIVERED' ? 'check' : status === 'OUT_FOR_DELIVERY' ? 'arrow' : 'box'} />{label || info.label}</span>
}
