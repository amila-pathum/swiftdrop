import { Link } from 'react-router-dom'
import { useAuth } from '../../../hooks/useAuth'
import { useCourierProfile } from '../../../hooks/courier/useCourierProfile'
import { useCourierDeliveries } from '../../../hooks/courier/useCourierDeliveries'
import { STATUS, address, courierError, dateLabel, firstName } from '../../../utils/courier/deliveryStatus'
import { dayKey, finishedAt, prioritise, recentlyFinished, schedule, summarise } from '../../../utils/courier/dashboard'
import { DashboardHeader, KpiCard, KpiGrid } from '../../../components/dashboard/Dashboard'
import Alert from '../../../components/Alert'
import Button, { ButtonLink } from '../../../components/Button'
import LoadingSpinner from '../../../components/LoadingSpinner'
import EmptyState from '../../../components/EmptyState'
import AvailabilityControl from '../../../components/courier/AvailabilityControl'
import CourierIcon from '../../../components/courier/CourierIcon'
import DeliveryStatusBadge from '../../../components/courier/DeliveryStatusBadge'
import '../../../components/courier/courier.css'
import './courierToday.css'

const SCHEDULE_LABEL = { overdue: 'Overdue', today: 'Due today', upcoming: 'Upcoming', unscheduled: 'Not scheduled' }
const STAGES = [['CREATED', 'To pick up'], ['PICKED_UP', 'Picked up'], ['IN_TRANSIT', 'In transit'], ['OUT_FOR_DELIVERY', 'Out for delivery']]
const deliveryPath = (parcel) => `/courier/deliveries/${encodeURIComponent(parcel.id)}`
const when = (value) => value ? dateLabel(value, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : 'Time not recorded'
const plural = (count, one, many = `${one}s`) => `${count} ${count === 1 ? one : many}`

function ScheduleChip({ delivery, today }) {
  const kind = schedule(delivery, today)
  const date = delivery.assignment.deliveryDate
  return <span className={`dash-schedule dash-schedule-${kind}`}>
    {SCHEDULE_LABEL[kind]}{date && kind !== 'today' ? ` · ${dateLabel(date, { day: 'numeric', month: 'short' })}` : ''}
  </span>
}

function UpNext({ delivery, today }) {
  const { parcel } = delivery
  const phone = parcel.receiverPhone?.replace(/[^+0-9]/g, '')
  return <section className="courier-panel dash-next" aria-labelledby="dash-next-title">
    <div className="dash-next-top">
      <span className="dash-eyebrow">Up next</span>
      <ScheduleChip delivery={delivery} today={today} />
    </div>
    <div className="dash-next-title">
      <h2 id="dash-next-title"><Link to={deliveryPath(parcel)}>{parcel.trackingNumber || parcel.id}</Link></h2>
      <DeliveryStatusBadge status={parcel.status} />
    </div>
    <dl className="dash-next-facts">
      <div><dt><CourierIcon name="person" size={15} />Recipient</dt><dd>{parcel.receiverName || 'Not provided'}</dd></div>
      <div><dt><CourierIcon name="pin" size={15} />{parcel.status === 'CREATED' ? 'Pick up from' : 'Deliver to'}</dt>
        <dd>{address(parcel, parcel.status === 'CREATED' ? 'pickup' : 'delivery')}</dd></div>
      {parcel.contentsDescription && <div><dt><CourierIcon name="box" size={15} />Contents</dt><dd>{parcel.contentsDescription}</dd></div>}
    </dl>
    <div className="dash-next-actions">
      <ButtonLink to={deliveryPath(parcel)}>{STATUS[parcel.status]?.action || 'Open delivery'}</ButtonLink>
      {phone && <a className="button button-outline" href={`tel:${phone}`}><CourierIcon name="phone" size={15} />Call recipient</a>}
    </div>
  </section>
}

function Stages({ stages }) {
  const total = Object.values(stages).reduce((sum, count) => sum + count, 0)
  return <section className="courier-panel dash-stages" aria-labelledby="dash-stages-title">
    <h2 id="dash-stages-title">Where your parcels are</h2>
    <ul>
      {STAGES.map(([status, label]) => <li key={status}>
        <div className="dash-stage-row"><span>{label}</span><strong>{stages[status]}</strong></div>
        <div className="dash-stage-bar" aria-hidden="true"><span className={`dash-stage-fill dash-stage-${status.toLowerCase()}`} style={{ width: total ? `${(stages[status] / total) * 100}%` : 0 }} /></div>
      </li>)}
    </ul>
  </section>
}

function ActiveList({ deliveries, today }) {
  if (!deliveries.length) return <EmptyState title="You're all caught up" description="There are no active deliveries assigned to you right now." />
  return <ul className="dash-list" aria-label="Active deliveries">
    {deliveries.map((delivery) => {
      const { parcel, assignment } = delivery
      return <li key={assignment.id} className="dash-row">
        <div className="dash-row-main">
          <Link className="dash-row-tracking" to={deliveryPath(parcel)} aria-label={`Open delivery ${parcel.trackingNumber || parcel.id}`}>{parcel.trackingNumber || parcel.id}</Link>
          <span className="dash-row-sub">{[parcel.contentsDescription, parcel.weight != null ? `${parcel.weight} kg` : null].filter(Boolean).join(' · ') || 'Parcel'}</span>
        </div>
        <div className="dash-row-recipient">
          <strong>{parcel.receiverName || 'Recipient not provided'}</strong>
          <span>{address(parcel, 'delivery')}</span>
        </div>
        <div className="dash-row-schedule"><ScheduleChip delivery={delivery} today={today} /></div>
        <div className="dash-row-status"><DeliveryStatusBadge status={parcel.status} /></div>
        <ButtonLink className="dash-row-action" size="sm" variant="outline" to={deliveryPath(parcel)}>{STATUS[parcel.status]?.action || 'Open'}</ButtonLink>
      </li>
    })}
  </ul>
}

function Recent({ deliveries }) {
  return <section className="courier-panel" aria-labelledby="dash-recent-title">
    <header className="courier-panel-heading"><h2 id="dash-recent-title">Recently completed</h2></header>
    {deliveries.length ? <ul className="dash-recent">
      {deliveries.map((delivery) => {
        const { parcel, assignment } = delivery
        const delivered = parcel.status === 'DELIVERED'
        return <li key={assignment.id}>
          <span className={`dash-recent-icon ${delivered ? 'is-success' : 'is-error'}`}><CourierIcon name={delivered ? 'check' : 'alert'} size={16} /></span>
          <div>
            <Link to={deliveryPath(parcel)}>{parcel.trackingNumber || parcel.id}</Link>
            <span>{parcel.receiverName || address(parcel, 'delivery')}</span>
          </div>
          <div className="dash-recent-meta"><DeliveryStatusBadge status={parcel.status} /><time dateTime={finishedAt(delivery) || undefined}>{when(finishedAt(delivery))}</time></div>
        </li>
      })}
    </ul> : <div className="courier-panel-body"><p className="courier-help">Delivered and failed parcels will appear here.</p></div>}
  </section>
}

export default function CourierTodayPage() {
  const { user } = useAuth()
  const profile = useCourierProfile()
  // Every assignment, not just today's: active parcels from earlier days must stay visible.
  const deliveries = useCourierDeliveries('all')
  const today = dayKey()
  const data = deliveries.data
  const summary = data ? summarise(data, today) : null
  const active = data ? prioritise(data, today) : []

  return <section className="courier-page courier-today-page">
    <DashboardHeader title={`Hello, ${firstName(profile.data?.name || user?.name)}`}
      subtitle={dateLabel(new Date().toISOString(), { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}
      actions={profile.data && <AvailabilityControl profile={profile.data} onChanged={profile.reload} />} />
    {profile.error && <Alert variant="warning">{courierError(profile.error, 'profile')}</Alert>}
    {deliveries.loading && !data && <div className="courier-loading" role="status"><LoadingSpinner /><span>Loading your deliveries...</span></div>}
    {deliveries.error && <Alert variant="error" title="Unable to load your deliveries"><p>{courierError(deliveries.error)}</p><Button variant="outline" size="sm" onClick={deliveries.reload}>Try again</Button></Alert>}
    {summary?.overdue > 0 && <Alert variant="warning" title={`${plural(summary.overdue, 'delivery', 'deliveries')} overdue`}>
      <p>{summary.overdue === 1 ? 'It was' : 'They were'} scheduled before today and {summary.overdue === 1 ? 'is' : 'are'} listed first below.</p>
    </Alert>}

    <KpiGrid label="Delivery summary">
      <KpiCard icon="package" label="Active deliveries" value={summary?.active} detail={summary && `${summary.dueToday} due today`} />
      <KpiCard icon="clock" label="Overdue" value={summary?.overdue} detail="Scheduled before today" tone="warning" highlight={summary?.overdue > 0} />
      <KpiCard icon="check" label="Delivered today" value={summary?.deliveredToday} detail={summary && (summary.failedToday ? `${summary.failedToday} failed today` : 'No failed attempts today')} tone="success" />
      <KpiCard icon="chart" label="Success rate" value={summary?.successRate == null ? (summary ? 'N/A' : null) : `${summary.successRate}%`} detail={summary && `${plural(summary.delivered, 'delivery', 'deliveries')} completed`} />
    </KpiGrid>

    {data && active.length > 0 && <div className="dash-overview">
      <UpNext delivery={active[0]} today={today} />
      <Stages stages={summary.stages} />
    </div>}

    <section className="courier-panel" aria-labelledby="courier-todo">
      <header className="courier-panel-heading">
        <h2 id="courier-todo">Active deliveries{data ? <span className="dash-count">{active.length}</span> : null}</h2>
        <Link className="dash-link" to="/courier/deliveries">View all deliveries</Link>
      </header>
      {data ? <ActiveList deliveries={active} today={today} />
        : <div className="courier-panel-body"><p className="courier-help">{deliveries.loading ? 'Loading assigned deliveries...' : 'Assigned deliveries could not be loaded. Resolve the message above and try again.'}</p></div>}
    </section>

    {data && <Recent deliveries={recentlyFinished(data)} />}
  </section>
}
