import { useCallback, useEffect, useRef, useState } from 'react'
import Button from '../../components/Button'
import LoadingSpinner from '../../components/LoadingSpinner'
import { Avatar, DashboardHeader, KpiCard, KpiGrid, Panel } from '../../components/dashboard/Dashboard'
import DashboardIcon from '../../components/dashboard/DashboardIcon'
import { dateTimeLabel } from '../../utils/dashboardFormat'
import { dispatchService } from '../../services/dispatchService'
import { availabilityLabel, statusLabel } from './dispatchFormat'
import './courierWorkload.css'

const AVAILABILITY_ORDER = { Available: 0, Busy: 1, Offline: 2, Inactive: 3 }
const AVAILABILITY_TONE = { Available: 'success', Busy: 'warning', Offline: '', Inactive: 'danger' }
const sentence = (value) => value ? value.charAt(0) + value.slice(1).toLowerCase() : '—'
const STATUS_TONE = { PICKED_UP: 'info', IN_TRANSIT: 'info', OUT_FOR_DELIVERY: 'warning', DELIVERED: 'success', FAILED: 'danger' }
// A courier with this many active deliveries is shown as fully loaded.
const FULL_LOAD = 5

function Pill({ tone = '', children }) {
  return <span className={`db-pill${tone ? ` db-pill-${tone}` : ''}`}><span className="db-pill-dot" aria-hidden="true" />{children}</span>
}

export default function CourierWorkloadPage() {
  const [couriers, setCouriers] = useState([])
  const [selectedCourier, setSelectedCourier] = useState(null)
  const [assignedParcels, setAssignedParcels] = useState([])
  const [parcelsLoading, setParcelsLoading] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const parcelsPanel = useRef(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setCouriers(await dispatchService.getCouriers())
    } catch (requestError) {
      setError(requestError.message || 'Unable to load courier workload.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    const timer = window.setTimeout(load, 0)
    return () => window.clearTimeout(timer)
  }, [load])

  useEffect(() => {
    if (selectedCourier) parcelsPanel.current?.scrollIntoView?.({ behavior: 'smooth', block: 'nearest' })
  }, [selectedCourier])

  const showAssignedParcels = async (courier) => {
    setSelectedCourier(courier)
    setAssignedParcels([])
    setParcelsLoading(true)
    setError('')
    try {
      const result = await dispatchService.getAssignments({
        state: 'ACTIVE', courierId: courier.courierId, pageSize: 100,
      })
      setAssignedParcels(result.content)
    } catch (requestError) {
      setError(requestError.message || 'Unable to load this courier’s active parcels.')
    } finally {
      setParcelsLoading(false)
    }
  }

  const sorted = [...couriers].sort((a, b) => AVAILABILITY_ORDER[availabilityLabel(a)] - AVAILABILITY_ORDER[availabilityLabel(b)]
    || (a.activeParcelCount || 0) - (b.activeParcelCount || 0) || String(a.name).localeCompare(String(b.name)))
  const active = couriers.filter((courier) => courier.active)
  const available = couriers.filter((courier) => courier.available).length
  const total = couriers.reduce((sum, courier) => sum + (Number(courier.activeParcelCount) || 0), 0)
  const busiest = couriers.reduce((top, courier) => (!top || courier.activeParcelCount > top.activeParcelCount ? courier : top), null)
  const ready = !loading || couriers.length > 0

  return (
    <section className="dispatch-page db-page courier-workload-page">
      <DashboardHeader title="Courier workload"
        subtitle="Active deliveries per courier from dispatch assignments, with availability from the courier service."
        actions={<Button variant="outline" onClick={load}><DashboardIcon name="refresh" size={16} />Refresh</Button>} />
      {error && <p className="dispatch-error" role="alert">{error}</p>}

      <KpiGrid label="Workload summary">
        <KpiCard icon="users" label="Couriers" value={ready ? couriers.length : null} detail={`${active.length} active profiles`} tone="neutral" />
        <KpiCard icon="check" label="Available now" value={ready ? available : null} detail="Can take new parcels" tone="success" />
        <KpiCard icon="truck" label="Active deliveries" value={ready ? total : null}
          detail={active.length ? `${(total / active.length).toFixed(1)} per active courier` : 'No active couriers'} />
        <KpiCard icon="chart" label="Busiest courier" value={ready ? (busiest ? busiest.activeParcelCount : 0) : null}
          detail={busiest ? busiest.name : 'No couriers'} tone="warning" />
      </KpiGrid>

      {loading && couriers.length === 0 ? (
        <div className="dispatch-loading"><LoadingSpinner label="Loading courier workload" /></div>
      ) : !couriers.length ? (
        <p className="db-panel db-empty">No courier profiles are available.</p>
      ) : (
        <ul className="cw-grid" aria-label="Couriers">
          {sorted.map((courier) => {
            const label = availabilityLabel(courier)
            const count = Number(courier.activeParcelCount) || 0
            const selected = selectedCourier?.courierId === courier.courierId
            return (
              <li key={courier.courierId} className={`cw-card${selected ? ' is-selected' : ''}`}>
                <div className="cw-card-top">
                  <Avatar name={courier.name} size={40} />
                  <div className="cw-card-name">
                    <strong title={courier.name}>{courier.name}</strong>
                    <Pill tone={AVAILABILITY_TONE[label]}>{label}</Pill>
                  </div>
                </div>
                <div className="cw-card-load">
                  <div className="cw-card-count"><strong>{count}</strong><span>active {count === 1 ? 'delivery' : 'deliveries'}</span></div>
                  <div className="cw-bar" aria-hidden="true">
                    <span className={count >= FULL_LOAD ? 'is-full' : ''} style={{ width: `${Math.min(100, (count / FULL_LOAD) * 100)}%` }} />
                  </div>
                </div>
                <Button variant={selected ? 'primary' : 'outline'} size="sm" fullWidth disabled={count === 0}
                  onClick={() => showAssignedParcels(courier)} aria-label={`Show active parcels of ${courier.name}`}>
                  {count === 0 ? 'No active parcels' : 'View active parcels'}
                </Button>
              </li>
            )
          })}
        </ul>
      )}

      {selectedCourier && (
        <div ref={parcelsPanel}>
          <Panel id="courier-parcels" title={`${selectedCourier.name}: active parcels`}
            description={parcelsLoading ? 'Loading…' : `${assignedParcels.length} ${assignedParcels.length === 1 ? 'parcel' : 'parcels'} assigned and not finished`}
            actions={<Button variant="outline" size="sm" onClick={() => setSelectedCourier(null)}>Close</Button>} flush>
            {parcelsLoading ? (
              <div className="dispatch-loading"><LoadingSpinner label="Loading active parcels" /></div>
            ) : !assignedParcels.length ? (
              <p className="db-empty">No active assigned parcels.</p>
            ) : (
              <div className="dispatch-table-wrap cw-table">
                <table className="dispatch-table">
                  <thead><tr><th>Parcel</th><th>Status</th><th>Assigned</th></tr></thead>
                  <tbody>{assignedParcels.map((assignment) => (
                    <tr key={assignment.assignmentId}>
                      <td className="cw-tracking">{assignment.trackingNumber}</td>
                      <td><Pill tone={STATUS_TONE[assignment.parcelStatus]}>{sentence(statusLabel(assignment.parcelStatus))}</Pill></td>
                      <td className="cw-muted">{dateTimeLabel(assignment.assignedAt)}</td>
                    </tr>
                  ))}</tbody>
                </table>
              </div>
            )}
          </Panel>
        </div>
      )}
    </section>
  )
}
