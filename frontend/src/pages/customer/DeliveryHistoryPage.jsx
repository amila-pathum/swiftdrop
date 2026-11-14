import { useCallback, useEffect, useMemo, useState } from 'react'
import Button from '../../components/Button'
import { ButtonLink } from '../../components/Button'
import LoadingSpinner from '../../components/LoadingSpinner'
import { parcelService } from '../../services/parcelService'

const PAGE_SIZE = 10

const STATUS_CONFIG = {
  CREATED:          { label: 'Created',          badgeClass: 'badge badge-info',    icon: '◎' },
  ASSIGNED:         { label: 'Assigned',          badgeClass: 'badge badge-warning', icon: '→' },
  PICKED_UP:        { label: 'Picked Up',         badgeClass: 'badge badge-warning', icon: '↑' },
  IN_TRANSIT:       { label: 'In Transit',        badgeClass: 'badge badge-info',    icon: '🚚' },
  OUT_FOR_DELIVERY: { label: 'Out for Delivery',  badgeClass: 'badge badge-warning', icon: '🚚' },
  DELIVERED:        { label: 'Delivered',         badgeClass: 'badge badge-success', icon: '✓' },
  CANCELLED:        { label: 'Cancelled',         badgeClass: 'badge badge-muted',   icon: '✕' },
  FAILED:           { label: 'Failed',            badgeClass: 'badge badge-error',   icon: '⊗' },
}

const DATE_FILTER_OPTIONS = [
  { value: 'ALL',    label: 'Any date' },
  { value: 'TODAY',  label: 'Today' },
  { value: '7DAYS',  label: 'Last 7 days' },
  { value: '30DAYS', label: 'Last 30 days' },
]

function formatDate(value) {
  if (!value) return '—'
  const d = new Date(value)
  return Number.isNaN(d.getTime())
    ? '—'
    : d.toLocaleDateString(undefined, { day: '2-digit', month: 'short', year: 'numeric' })
}

function matchesDateFilter(createdAt, filter) {
  if (filter === 'ALL' || !createdAt) return true
  const d = new Date(createdAt)
  const now = new Date()
  if (filter === 'TODAY') {
    return (
      d.getFullYear() === now.getFullYear() &&
      d.getMonth() === now.getMonth() &&
      d.getDate() === now.getDate()
    )
  }
  const days = filter === '7DAYS' ? 7 : 30
  return d >= new Date(now.getTime() - days * 24 * 60 * 60 * 1000)
}

export default function DeliveryHistoryPage() {
  const [parcels, setParcels]         = useState([])
  const [loading, setLoading]         = useState(true)
  const [error, setError]             = useState(null)

  const [search, setSearch]               = useState('')
  const [statusFilter, setStatusFilter]   = useState('ALL')
  const [dateFilter, setDateFilter]       = useState('ALL')
  const [page, setPage]                   = useState(1)

  const [cancelling, setCancelling]       = useState({})
  const [downloading, setDownloading]     = useState({})
  const [actionSuccess, setActionSuccess] = useState('')
  const [actionError, setActionError]     = useState(null)

  const loadParcels = useCallback(() => {
    let ignore = false
    setLoading(true)
    setError(null)
    parcelService.getMyParcels()
      .then((data) => {
        if (!ignore) {
          setParcels(Array.isArray(data) ? data : [])
          setLoading(false)
        }
      })
      .catch((err) => {
        if (!ignore && err?.name !== 'AbortError') {
          setError(err)
          setLoading(false)
        }
      })
    return () => { ignore = true }
  }, [])

  useEffect(() => loadParcels(), [loadParcels])

  // Reset to page 1 whenever filters change
  useEffect(() => { setPage(1) }, [search, statusFilter, dateFilter])

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase()
    return parcels.filter((p) => {
      if (statusFilter !== 'ALL' && p.status !== statusFilter) return false
      if (!matchesDateFilter(p.createdAt, dateFilter)) return false
      if (q && !String(p.trackingNumber ?? '').toLowerCase().includes(q)) return false
      return true
    })
  }, [parcels, search, statusFilter, dateFilter])

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const paginated  = useMemo(() => {
    const start = (page - 1) * PAGE_SIZE
    return filtered.slice(start, start + PAGE_SIZE)
  }, [filtered, page])

  const showFrom = filtered.length === 0 ? 0 : (page - 1) * PAGE_SIZE + 1
  const showTo   = Math.min(page * PAGE_SIZE, filtered.length)

  const handleCancel = async (parcel) => {
    if (cancelling[parcel.id]) return
    setCancelling((prev) => ({ ...prev, [parcel.id]: true }))
    setActionError(null)
    setActionSuccess('')
    try {
      const updated = await parcelService.cancelParcel(parcel.id)
      setParcels((prev) => prev.map((p) => (p.id === parcel.id ? { ...p, ...updated } : p)))
      setActionSuccess(`Parcel ${parcel.trackingNumber ?? parcel.id} has been cancelled.`)
    } catch (err) {
      setActionError(err.message || 'Failed to cancel parcel. Please try again.')
    } finally {
      setCancelling((prev) => ({ ...prev, [parcel.id]: false }))
    }
  }

  const handleDownloadReceipt = async (parcel) => {
    if (downloading[parcel.id]) return
    setDownloading((prev) => ({ ...prev, [parcel.id]: true }))
    setActionError(null)
    setActionSuccess('')
    try {
      await parcelService.downloadReceipt(parcel.id)
    } catch (err) {
      setActionError(err.message || 'Failed to download receipt. Please try again.')
    } finally {
      setDownloading((prev) => ({ ...prev, [parcel.id]: false }))
    }
  }

  return (
    <div>
      {/* Breadcrumb */}
      <nav className="page-breadcrumb" aria-label="Breadcrumb">
        <span>Overview</span>
        <span aria-hidden="true">›</span>
        <strong>My Deliveries</strong>
      </nav>

      {/* Page header */}
      <div className="page-header-row">
        <h1 className="page-title">My Deliveries</h1>
        <div className="page-header-actions">
          <ButtonLink to="/customer/create-parcel" variant="primary">
            ⊕ Create Delivery
          </ButtonLink>
        </div>
      </div>

      {/* Action feedback banners */}
      {actionSuccess && (
        <div className="alert alert-success" role="status" style={{ marginBottom: '16px' }}>
          <span>✓ {actionSuccess}</span>
          <button
            type="button"
            className="alert-dismiss"
            onClick={() => setActionSuccess('')}
            aria-label="Dismiss notification"
          >
            Dismiss
          </button>
        </div>
      )}
      {actionError && (
        <div className="alert alert-error" role="alert" style={{ marginBottom: '16px' }}>
          <span>{actionError}</span>
          <button
            type="button"
            className="alert-dismiss"
            onClick={() => setActionError(null)}
            aria-label="Dismiss error"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Loading state */}
      {loading ? (
        <div className="card status-card status-card-loading" style={{ margin: '30px auto' }}>
          <LoadingSpinner />
          <p role="status">Loading your deliveries…</p>
        </div>
      ) : error ? (
        /* Error state */
        <div className="card status-card" role="alert" style={{ margin: '30px auto' }}>
          <h2 className="card-title">Unable to load deliveries</h2>
          <p className="card-subtitle">
            {error.message || 'An error occurred while fetching your deliveries.'}
          </p>
          <Button onClick={loadParcels}>Try again</Button>
        </div>
      ) : (
        /* Main table card — always shown once loaded (even when empty, to show filters) */
        <section className="card-table" aria-label="Parcel delivery history">

          {/* Filter bar */}
          <div className="filter-bar">
            <div className="search-input-wrapper">
              <span className="search-icon" aria-hidden="true">🔍</span>
              <input
                type="search"
                className="input"
                placeholder="Search by tracking number"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                aria-label="Search by tracking number"
              />
            </div>

            <select
              className="filter-select"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              aria-label="Filter by status"
            >
              <option value="ALL">All statuses</option>
              {Object.entries(STATUS_CONFIG).map(([key, { label }]) => (
                <option key={key} value={key}>{label}</option>
              ))}
            </select>

            <select
              className="filter-select"
              value={dateFilter}
              onChange={(e) => setDateFilter(e.target.value)}
              aria-label="Filter by date"
            >
              {DATE_FILTER_OPTIONS.map(({ value, label }) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </select>
          </div>

          {/* Table */}
          <div className="table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th scope="col">Tracking no.</th>
                  <th scope="col">Destination</th>
                  <th scope="col">Created</th>
                  <th scope="col">Fee</th>
                  <th scope="col">Status</th>
                  <th scope="col"></th>
                </tr>
              </thead>
              <tbody>
                {paginated.length === 0 ? (
                  <tr>
                    <td colSpan="6" className="table-empty-state">
                      <p className="table-empty-title">
                        {parcels.length === 0
                          ? 'No deliveries yet'
                          : 'No results match your filters'}
                      </p>
                      <p>
                        {parcels.length === 0
                          ? 'Your parcel deliveries will appear here once you send one.'
                          : 'Try adjusting your search or filter criteria.'}
                      </p>
                    </td>
                  </tr>
                ) : (
                  paginated.map((parcel) => {
                    const cfg = STATUS_CONFIG[parcel.status] ?? {
                      label: parcel.status ?? '—',
                      badgeClass: 'badge badge-muted',
                      icon: '',
                    }
                    return (
                      <tr key={parcel.id}>
                        {/* Tracking number */}
                        <td>
                          <span style={{
                            fontWeight: 600,
                            color: 'var(--color-primary)',
                            fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                            fontSize: '13px',
                          }}>
                            {parcel.trackingNumber ?? '—'}
                          </span>
                        </td>

                        {/* Destination: delivery address + optional receiver name */}
                        <td>
                          <div style={{ fontWeight: 600, fontSize: '14px', color: 'var(--color-text)' }}>
                            {parcel.deliveryAddress ?? '—'}
                          </div>
                          {parcel.receiverName && (
                            <div style={{ fontSize: '12px', color: 'var(--color-primary)', marginTop: '2px' }}>
                              {parcel.receiverName}
                            </div>
                          )}
                        </td>

                        {/* Created date */}
                        <td>{formatDate(parcel.createdAt)}</td>

                        {/* Fee */}
                        <td style={{ fontWeight: 500 }}>
                          {parcel.charge != null
                            ? `LKR ${Number(parcel.charge).toLocaleString()}`
                            : '—'}
                        </td>

                        {/* Status badge */}
                        <td>
                          <span className={cfg.badgeClass}>
                            {cfg.icon && <span aria-hidden="true">{cfg.icon}</span>}
                            {cfg.label}
                          </span>
                        </td>

                        {/* Actions */}
                        <td>
                          <div className="actions-group">
                            {parcel.status === 'CREATED' && (
                              <button
                                type="button"
                                className="action-link action-link-danger"
                                disabled={!!cancelling[parcel.id]}
                                onClick={() => handleCancel(parcel)}
                              >
                                {cancelling[parcel.id] ? 'Cancelling…' : 'Cancel'}
                              </button>
                            )}
                            {parcel.status === 'DELIVERED' && (
                              <button
                                type="button"
                                className="action-link action-link-primary"
                                disabled={!!downloading[parcel.id]}
                                onClick={() => handleDownloadReceipt(parcel)}
                              >
                                {downloading[parcel.id] ? 'Downloading…' : 'Download Receipt'}
                              </button>
                            )}
                            <Button variant="outline" size="sm">
                              View Details
                            </Button>
                          </div>
                        </td>
                      </tr>
                    )
                  })
                )}
              </tbody>
            </table>
          </div>

          {/* Footer: row count + pagination */}
          <div className="card-table-footer">
            <span>
              {filtered.length === 0
                ? 'No results'
                : `Showing ${showFrom}–${showTo} of ${filtered.length}`}
            </span>

            <div className="pagination" role="navigation" aria-label="Pagination">
              <button
                type="button"
                className="pagination-btn"
                disabled={page <= 1}
                onClick={() => setPage((p) => p - 1)}
                aria-label="Previous page"
              >
                ‹
              </button>
              <span style={{ padding: '0 8px', fontSize: '13px', color: 'var(--color-text)' }}>
                Page {page} of {totalPages}
              </span>
              <button
                type="button"
                className="pagination-btn"
                disabled={page >= totalPages}
                onClick={() => setPage((p) => p + 1)}
                aria-label="Next page"
              >
                ›
              </button>
            </div>
          </div>
        </section>
      )}
    </div>
  )
}

