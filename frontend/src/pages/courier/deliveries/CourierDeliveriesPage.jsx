import { useState } from 'react'
import PageHeader from '../../../components/PageHeader'
import Alert from '../../../components/Alert'
import Button from '../../../components/Button'
import LoadingSpinner from '../../../components/LoadingSpinner'
import DeliveryTabs from '../../../components/courier/DeliveryTabs'
import DeliveryGrid from '../../../components/courier/DeliveryGrid'
import { useCourierDeliveries } from '../../../hooks/courier/useCourierDeliveries'
import { activeDelivery, completedDelivery, courierError } from '../../../utils/courier/deliveryStatus'
import '../../../components/courier/courier.css'
import './courierDeliveries.css'

export default function CourierDeliveriesPage() {
  const [selected, setSelected] = useState('active')
  const resource = useCourierDeliveries('all')
  const groups = resource.data ? { active: resource.data.filter(activeDelivery), completed: resource.data.filter(completedDelivery), all: resource.data } : null
  return <section className="courier-page courier-deliveries-page">
    <PageHeader title="Deliveries" description="Only parcels assigned to you are shown." />
    <DeliveryTabs selected={selected} onChange={setSelected} counts={Object.fromEntries(['active', 'completed', 'all'].map((key) => [key, groups?.[key].length ?? '—']))} />
    {resource.loading && <div role="status" className="courier-loading"><LoadingSpinner /><span>Loading your deliveries...</span></div>}
    {resource.error && <Alert variant="error" title="Unable to load deliveries"><p>{courierError(resource.error)}</p><Button variant="outline" size="sm" onClick={resource.reload}>Try again</Button></Alert>}
    <div role="tabpanel" id="courier-delivery-panel" aria-labelledby={`courier-tab-${selected}`}>
      {groups ? <DeliveryGrid deliveries={groups[selected]} emptyTitle={selected === 'active' ? 'No active deliveries' : selected === 'completed' ? 'No completed deliveries yet' : 'No assignments yet'} emptyDescription="Your dispatcher assignments and confirmed outcomes will appear here." /> : <p className="courier-help">{resource.loading ? 'Loading assignments...' : 'Delivery information is currently unavailable.'}</p>}
    </div>
  </section>
}
