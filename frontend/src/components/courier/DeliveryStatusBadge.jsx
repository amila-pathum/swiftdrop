import { STATUS } from '../../utils/courier/deliveryStatus'
import CourierIcon from './CourierIcon'

export default function DeliveryStatusBadge({ status, archived = false }) {
  const info = archived ? { label: 'Reassigned', tone: 'neutral' } : STATUS[status] || { label: 'Status unavailable', tone: 'neutral' }
  return <span className={`courier-status-badge courier-status-${info.tone}`}><CourierIcon size={13} name={status === 'DELIVERED' ? 'check' : status === 'OUT_FOR_DELIVERY' ? 'arrow' : 'box'} />{info.label}</span>
}
