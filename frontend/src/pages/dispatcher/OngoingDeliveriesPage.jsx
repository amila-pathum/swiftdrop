import { useCallback, useEffect, useState } from 'react'
import Button from '../../components/Button'
import LoadingSpinner from '../../components/LoadingSpinner'
import { dispatchService } from '../../services/dispatchService'
import DeliveryDetailDialog from './DeliveryDetailDialog'
import { EMPTY_PAGE, formatDateTime, statusLabel } from './dispatchFormat'
const DELIVERY_STATUSES = ['PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED']

export default function OngoingDeliveriesPage() {
  const [result, setResult] = useState(EMPTY_PAGE)
  const [filters, setFilters] = useState({})
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState(null)
  const closeDetail = useCallback(() => setSelected(null), [])

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setResult(await dispatchService.getDeliveries({ ...filters, page, pageSize: 20 }))
    } catch (requestError) {
      setError(requestError.message || 'Unable to load deliveries.')
    } finally {
      setLoading(false)
    }
  }, [filters, page])

  useEffect(() => {
    const timer = window.setTimeout(load, 0)
    return () => window.clearTimeout(timer)
  }, [load])

  const submitFilters = (event) => {
    event.preventDefault()
    setFilters(Object.fromEntries(new FormData(event.currentTarget)))
    setPage(0)
  }

  return (
    <section className="dispatch-page">
      <header className="dispatch-page-header">
        <div>
          <h1>Ongoing deliveries</h1>
          <p>Parcels out with a courier, by their latest status. Pick Delivered or Failed to see finished ones. Select a tracking number to see the courier's notes and photos.</p>
        </div>
        <Button variant="secondary" onClick={load}>Refresh</Button>
      </header>

      <section className="dispatch-section card">
        <form className="dispatch-filters" onSubmit={submitFilters}>
          <label>Tracking number<input name="trackingNumber" placeholder="Search tracking number" /></label>
          <label>Status<select name="status" defaultValue="">
            <option value="">All ongoing (picked up to out for delivery)</option>
            {DELIVERY_STATUSES.map((status) => <option key={status} value={status}>{statusLabel(status)}</option>)}
          </select></label>
          <Button type="submit">Search</Button>
        </form>

        {error && <p className="dispatch-error" role="alert">{error}</p>}
        {loading && result.content.length === 0 ? (
          <div className="dispatch-loading"><LoadingSpinner label="Loading deliveries" /></div>
        ) : result.content.length === 0 ? (
          <p className="dispatch-empty">No deliveries match these filters.</p>
        ) : (
          <>
            <div className="dispatch-table-wrap">
              <table className="dispatch-table">
                <thead><tr><th>Tracking number</th><th>Status</th><th>Courier</th><th>Last update</th></tr></thead>
                <tbody>{result.content.map((parcel) => (
                  <tr key={parcel.parcelId}>
                    <td>
                      <button className="dispatch-link-button" onClick={() => setSelected(parcel.parcelId)}>
                        {parcel.trackingNumber}
                      </button>
                    </td>
                    <td><span className={`dispatch-status status-${parcel.status.toLowerCase()}`}>
                      {statusLabel(parcel.status)}</span></td>
                    <td>{parcel.courierName || '—'}</td>
                    <td>{formatDateTime(parcel.lastUpdatedAt)}</td>
                  </tr>
                ))}</tbody>
              </table>
            </div>
            <div className="dispatch-pagination">
              <span>{result.totalElements} deliveries</span>
              <div>
                <Button variant="secondary" disabled={result.page <= 0} onClick={() => setPage(result.page - 1)}>
                  Previous
                </Button>
                <span>Page {result.page + 1} of {Math.max(result.totalPages, 1)}</span>
                <Button variant="secondary" disabled={result.page + 1 >= result.totalPages}
                  onClick={() => setPage(result.page + 1)}>Next</Button>
              </div>
            </div>
          </>
        )}
      </section>
      {selected && <DeliveryDetailDialog parcelId={selected} onClose={closeDetail} />}
    </section>
  )
}
