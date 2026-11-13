import { useCallback, useEffect, useState } from 'react'
import Button from '../../components/Button'
import LoadingSpinner from '../../components/LoadingSpinner'
import { dispatchService } from '../../services/dispatchService'

const EMPTY_PAGE = { content: [], page: 0, totalPages: 0, totalElements: 0 }
const DELIVERY_STATUSES = ['PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED']

export default function OngoingDeliveriesPage() {
  const [result, setResult] = useState(EMPTY_PAGE)
  const [filters, setFilters] = useState({})
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

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
          <p>Find active and recently completed delivery records by status or tracking number.</p>
        </div>
        <Button variant="secondary" onClick={load}>Refresh</Button>
      </header>

      <section className="dispatch-section card">
        <form className="dispatch-filters" onSubmit={submitFilters}>
          <label>Tracking number<input name="trackingNumber" placeholder="Search tracking number" /></label>
          <label>Status<select name="status" defaultValue="">
            <option value="">All delivery statuses</option>
            {DELIVERY_STATUSES.map((status) => <option key={status}>{status}</option>)}
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
                <thead><tr><th>Tracking number</th><th>Route</th><th>Status</th><th>Courier</th><th>Updated</th></tr></thead>
                <tbody>{result.content.map((parcel) => (
                  <tr key={parcel.parcelId}>
                    <td>{parcel.trackingNumber}</td>
                    <td>{parcel.pickupArea} → {parcel.dropoffArea}</td>
                    <td><span className={`dispatch-status status-${parcel.status.toLowerCase()}`}>
                      {parcel.status.replaceAll('_', ' ')}</span></td>
                    <td>{parcel.courierName || '—'}</td>
                    <td>{new Date(parcel.updatedAt).toLocaleString()}</td>
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
    </section>
  )
}
