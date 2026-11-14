import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import LoadingSpinner from '../../components/LoadingSpinner'
import { parcelService } from '../../services/parcelService'

const PAGE_SIZE = 10

function label(status) {
  return String(status || 'UNKNOWN').replaceAll('_', ' ')
}

function money(value) {
  const amount = Number(value)
  return Number.isFinite(amount) ? `LKR ${amount.toFixed(2)}` : '—'
}

export default function MerchantOrdersPage() {
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [reload, setReload] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    parcelService.getMerchantParcels(page, PAGE_SIZE, controller.signal)
      .then((data) => setResult(data))
      .catch((reason) => {
        if (reason?.name !== 'AbortError') setError(reason)
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [page, reload])

  const orders = Array.isArray(result?.content) ? result.content : []
  const totalPages = Number(result?.totalPages) || 0
  const loadPage = (nextPage) => {
    setLoading(true)
    setError(null)
    setPage(nextPage)
  }
  const retry = () => {
    setLoading(true)
    setError(null)
    setReload((value) => value + 1)
  }

  return (
    <div className="merchant-scope">
      <nav className="page-breadcrumb" aria-label="Breadcrumb">
        <span>Workspace</span><span>/</span><strong>Orders</strong>
      </nav>
      <header className="page-header-row">
        <div>
          <h1 className="page-title">Merchant orders</h1>
          <p className="page-subtitle">Parcels created by your authenticated merchant account.</p>
        </div>
      </header>

      {loading ? (
        <div className="card status-card"><LoadingSpinner /><p role="status">Loading merchant orders…</p></div>
      ) : error ? (
        <div className="card status-card" role="alert">
          <h2 className="card-title">Unable to load merchant orders</h2>
          <p>{error.message || 'Please try again.'}</p>
          <button type="button" className="btn btn-primary" onClick={retry}>Try again</button>
        </div>
      ) : orders.length === 0 ? (
        <div className="card status-card">
          <h2 className="card-title">No merchant orders yet</h2>
          <p>Orders created through your merchant account will appear here.</p>
        </div>
      ) : (
        <section className="card-table" aria-label="Merchant parcel orders">
          <div className="card-table-header">
            <div className="card-table-title">All orders <span className="card-table-subtitle">{result.totalElements} total</span></div>
          </div>
          <div className="table-responsive">
            <table>
              <thead><tr><th>Tracking number</th><th>Receiver</th><th>Status</th><th>Charge</th><th>Created</th><th>Details</th></tr></thead>
              <tbody>
                {orders.map((order) => (
                  <tr key={order.id}>
                    <td><code>{order.trackingNumber}</code></td>
                    <td>{order.receiverName || '—'}</td>
                    <td><span className="badge">{label(order.status)}</span></td>
                    <td>{money(order.charge)}</td>
                    <td>{order.createdAt ? new Date(order.createdAt).toLocaleString() : '—'}</td>
                    <td><Link to={`/track/${encodeURIComponent(order.trackingNumber)}`}>Track</Link></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="page-header-actions" style={{ padding: '16px', justifyContent: 'flex-end' }}>
            <button type="button" className="btn btn-secondary" disabled={page === 0} onClick={() => loadPage(page - 1)}>Previous</button>
            <span>Page {page + 1} of {Math.max(totalPages, 1)}</span>
            <button type="button" className="btn btn-secondary" disabled={page + 1 >= totalPages} onClick={() => loadPage(page + 1)}>Next</button>
          </div>
        </section>
      )}
    </div>
  )
}
