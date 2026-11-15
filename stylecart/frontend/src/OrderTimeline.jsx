export default function OrderTimeline({ order, statusName }) {
  const hasCreatedEvent = order.timeline.some(event => event.status === 'CREATED')
  return <ul className="timeline">
    <li><span/><div><strong>Order received by StyleCart</strong><small>{new Date(order.createdAt).toLocaleString()}</small></div></li>
    {order.trackingNumber && !hasCreatedEvent && <li>
      <span/><div><strong>Delivery created</strong><small>Shipment confirmed by SwiftDrop</small></div>
    </li>}
    {order.timeline.map(event => <li key={event.eventId}>
      <span/><div><strong>{statusName(event.status)}</strong><small>{new Date(event.occurredAt).toLocaleString()}</small></div>
    </li>)}
  </ul>
}
