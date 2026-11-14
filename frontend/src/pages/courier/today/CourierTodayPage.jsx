import { useAuth } from '../../../hooks/useAuth'
import { useCourierProfile } from '../../../hooks/courier/useCourierProfile'
import { useCourierDeliveries } from '../../../hooks/courier/useCourierDeliveries'
import { activeDelivery, completedDelivery, courierError, dateLabel, firstName } from '../../../utils/courier/deliveryStatus'
import PageHeader from '../../../components/PageHeader'
import Alert from '../../../components/Alert'
import Button from '../../../components/Button'
import LoadingSpinner from '../../../components/LoadingSpinner'
import AvailabilityControl from '../../../components/courier/AvailabilityControl'
import DeliveryGrid from '../../../components/courier/DeliveryGrid'
import TodaySummary from './components/TodaySummary'
import '../../../components/courier/courier.css'
import './courierToday.css'

export default function CourierTodayPage() {
  const { user } = useAuth()
  const profile = useCourierProfile()
  const deliveries = useCourierDeliveries('today')
  return <section className="courier-page courier-today-page">
    <PageHeader title={`Hello, ${firstName(profile.data?.name || user?.name)}`} description={dateLabel(new Date().toISOString(), { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}
      actions={profile.data && <AvailabilityControl profile={profile.data} onChanged={profile.reload} />} />
    {profile.error && <Alert variant="warning">{courierError(profile.error, 'profile')}</Alert>}
    {deliveries.loading && <div className="courier-loading" role="status"><LoadingSpinner /><span>Loading today's deliveries...</span></div>}
    {deliveries.error && <Alert variant="error" title="Unable to load today's deliveries"><p>{courierError(deliveries.error)}</p><Button variant="outline" size="sm" onClick={deliveries.reload}>Try again</Button></Alert>}
    {deliveries.data && <>
      <TodaySummary deliveries={deliveries.data} />
      <section className="courier-card-section" aria-labelledby="courier-todo"><h2 id="courier-todo">To do</h2><DeliveryGrid deliveries={deliveries.data.filter(activeDelivery)} emptyTitle="You're all caught up" emptyDescription="There are no active deliveries assigned to you for today." /></section>
      <section className="courier-card-section" aria-labelledby="courier-completed"><h2 id="courier-completed">Completed today</h2><DeliveryGrid deliveries={deliveries.data.filter(completedDelivery)} emptyTitle="No completed deliveries yet" emptyDescription="Delivered and failed attempts will appear here after confirmation." /></section>
    </>}
  </section>
}
