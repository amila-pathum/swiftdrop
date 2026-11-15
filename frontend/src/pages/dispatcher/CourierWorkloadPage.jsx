import { useCallback, useEffect, useState } from 'react'
import Button from '../../components/Button'
import LoadingSpinner from '../../components/LoadingSpinner'
import { dispatchService } from '../../services/dispatchService'
import { availabilityLabel, formatDateTime, statusLabel } from './dispatchFormat'

export default function CourierWorkloadPage() {
  const [couriers, setCouriers] = useState([])
  const [selectedCourier, setSelectedCourier] = useState(null)
  const [assignedParcels, setAssignedParcels] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

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

  const showAssignedParcels = async (courier) => {
    setSelectedCourier(courier)
    setAssignedParcels([])
    setError('')
    try {
      const result = await dispatchService.getAssignments({
        state: 'ACTIVE', courierId: courier.courierId, pageSize: 100,
      })
      setAssignedParcels(result.content)
    } catch (requestError) {
      setError(requestError.message || 'Unable to load this courier’s active parcels.')
    }
  }

  return (
    <section className="dispatch-page">
      <header className="dispatch-page-header">
        <div>
          <h1>Courier workload</h1>
          <p>Active delivery counts come from Dispatch’s assignment records; availability from Courier service.</p>
        </div>
        <Button variant="secondary" onClick={load}>Refresh</Button>
      </header>
      {error && <p className="dispatch-error" role="alert">{error}</p>}
      {loading && couriers.length === 0 ? (
        <div className="dispatch-loading"><LoadingSpinner label="Loading courier workload" /></div>
      ) : (
        <section className="dispatch-courier-grid">
          {couriers.map((courier) => (
            <article className="dispatch-courier-card card" key={courier.courierId}>
              <div>
                <strong>{courier.name}</strong>
                <span className={courier.available ? 'availability available' : 'availability unavailable'}>
                  {availabilityLabel(courier)}
                </span>
              </div>
              <div className="dispatch-courier-card-actions">
                <span className="workload-count">{courier.activeParcelCount}<small> active deliveries</small></span>
                <Button variant="secondary" onClick={() => showAssignedParcels(courier)}
                  aria-label={`Show active parcels of ${courier.name}`}>
                  Active parcels
                </Button>
              </div>
            </article>
          ))}
          {!couriers.length && <p className="dispatch-empty">No courier profiles are available.</p>}
        </section>
      )}
      {selectedCourier && (
        <section className="dispatch-section card">
          <div className="dispatch-section-heading">
            <div><h2>{selectedCourier.name} — active parcels</h2></div>
            <Button variant="secondary" onClick={() => setSelectedCourier(null)}>Close</Button>
          </div>
          {!assignedParcels.length ? (
            <p className="dispatch-empty">No active assigned parcels.</p>
          ) : (
            <div className="dispatch-table-wrap">
              <table className="dispatch-table">
                <thead><tr><th>Parcel</th><th>Status</th><th>Assigned at</th></tr></thead>
                <tbody>{assignedParcels.map((assignment) => (
                  <tr key={assignment.assignmentId}>
                    <td>{assignment.trackingNumber}</td>
                    <td>{statusLabel(assignment.parcelStatus)}</td>
                    <td>{formatDateTime(assignment.assignedAt)}</td>
                  </tr>
                ))}</tbody>
              </table>
            </div>
          )}
        </section>
      )}
    </section>
  )
}
