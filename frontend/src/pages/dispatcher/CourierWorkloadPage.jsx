import { useCallback, useEffect, useState } from 'react'
import Button from '../../components/Button'
import LoadingSpinner from '../../components/LoadingSpinner'
import { dispatchService } from '../../services/dispatchService'

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
      setAssignedParcels(await dispatchService.getCourierParcels(courier.courierId))
    } catch (requestError) {
      setError(requestError.message || 'Unable to load this courier’s active parcels.')
    }
  }

  return (
    <section className="dispatch-page">
      <header className="dispatch-page-header">
        <div>
          <h1>Courier workload</h1>
          <p>Active assignment counts are calculated from Dispatch’s assignment records.</p>
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
                <span className={courier.active && courier.available
                  ? 'availability available' : 'availability unavailable'}>
                  {!courier.active ? 'Inactive' : courier.available ? 'Available' : 'Unavailable'}
                </span>
              </div>
              <div className="dispatch-courier-card-actions">
                <span className="workload-count">{courier.activeParcelCount}<small> active parcels</small></span>
                <Button variant="secondary" onClick={() => showAssignedParcels(courier)}>
                  Assigned parcels
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
                <tbody>{assignedParcels.map((parcel) => (
                  <tr key={parcel.parcelId}>
                    <td>{parcel.trackingNumber || parcel.parcelId}</td>
                    <td>{parcel.status || 'CREATED'}</td>
                    <td>{new Date(parcel.assignedAt).toLocaleString()}</td>
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
