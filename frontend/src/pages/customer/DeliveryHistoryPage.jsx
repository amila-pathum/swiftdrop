import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { ButtonLink } from '../../components/Button'
import LoadingSpinner from '../../components/LoadingSpinner'
import { parcelService } from '../../services/parcelService'

const PAGE_SIZE = 10

const STATUS_CONFIG = {
  OUT_FOR_DELIVERY: {
    label: 'Out for Delivery',
    badgeClass: 'delivery-badge delivery-badge-out-for-delivery',
    icon: (
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <line x1="22" y1="2" x2="11" y2="13" />
        <polygon points="22 2 15 22 11 13 2 9 22 2" />
      </svg>
    ),
  },
  IN_TRANSIT: {
    label: 'In Transit',
    badgeClass: 'delivery-badge delivery-badge-in-transit',
    icon: (
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <rect x="1" y="3" width="15" height="13" />
        <polygon points="16 8 20 8 23 11 23 16 16 16 16 8" />
        <circle cx="5.5" cy="18.5" r="2.5" />
        <circle cx="18.5" cy="18.5" r="2.5" />
      </svg>
    ),
  },
  FAILED: {
    label: 'Failed',
    badgeClass: 'delivery-badge delivery-badge-failed',
    icon: (
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <circle cx="12" cy="12" r="10" />
        <line x1="15" y1="9" x2="9" y2="15" />
        <line x1="9" y1="9" x2="15" y2="15" />
      </svg>
    ),
  },
  DELIVERED: {
    label: 'Delivered',
    badgeClass: 'delivery-badge delivery-badge-delivered',
    icon: (
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <circle cx="12" cy="12" r="10" />
        <polyline points="9 11 11.5 13.5 15.5 9.5" />
      </svg>
    ),
  },
  CREATED: {
    label: 'Created',
    badgeClass: 'delivery-badge delivery-badge-created',
    icon: (
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <circle cx="12" cy="12" r="10" />
        <polyline points="12 6 12 12 16 14" />
      </svg>
    ),
  },
  ASSIGNED: {
    label: 'Assigned',
    badgeClass: 'delivery-badge delivery-badge-assigned',
    icon: (
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <line x1="5" y1="12" x2="19" y2="12" />
        <polyline points="12 5 19 12 12 19" />
      </svg>
    ),
  },
  PICKED_UP: {
    label: 'Picked Up',
    badgeClass: 'delivery-badge delivery-badge-picked-up',
    icon: (
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <polyline points="18 15 12 9 6 15" />
      </svg>
    ),
  },
  CANCELLED: {
    label: 'Cancelled',
    badgeClass: 'delivery-badge delivery-badge-cancelled',
    icon: (
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <circle cx="12" cy="12" r="10" />
        <line x1="4.93" y1="4.93" x2="19.07" y2="19.07" />
      </svg>
    ),
  },
}

const DATE_FILTER_OPTIONS = [
  { value: 'ALL', label: 'Any date' },
  { value: 'TODAY', label: 'Today' },
  { value: '7DAYS', label: 'Last 7 days' },
  { value: '30DAYS', label: 'Last 30 days' },
]

function formatDate(value) {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return '—'
  const day = String(d.getDate()).padStart(2, '0')
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
  const month = months[d.getMonth()]
  const year = d.getFullYear()
  return `${day} ${month} ${year}`
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
  const [parcels, setParcels] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [dateFilter, setDateFilter] = useState('ALL')
  const [page, setPage] = useState(1)

  const [cancelling, setCancelling] = useState({})
  const [downloading, setDownloading] = useState({})
  const [actionSuccess, setActionSuccess] = useState('')
  const [actionError, setActionError] = useState(null)
  const [assistantMessage, setAssistantMessage] = useState(false)

  const loadParcels = useCallback(() => {
    const controller = new AbortController()
    Promise.resolve()
      .then(() => {
        if (controller.signal.aborted) return null
        setLoading(true)
        setError(null)
        return parcelService.getMyParcels(0, 100, controller.signal)
      })
      .then((data) => {
        if (!controller.signal.aborted && data !== null) {
          const items = Array.isArray(data)
            ? data
            : (Array.isArray(data?.content) ? data.content : [])
          setParcels(items)
          setLoading(false)
        }
      })
      .catch((err) => {
        if (!controller.signal.aborted && err?.name !== 'AbortError') {
          setError(err)
          setLoading(false)
        }
      })
    return () => controller.abort()
  }, [])

  useEffect(() => loadParcels(), [loadParcels])

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

  const paginated = useMemo(() => {
    const start = (page - 1) * PAGE_SIZE
    return filtered.slice(start, start + PAGE_SIZE)
  }, [filtered, page])

  const showFrom = filtered.length === 0 ? 0 : (page - 1) * PAGE_SIZE + 1
  const showTo = Math.min(page * PAGE_SIZE, filtered.length)

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
    <div className="delivery-page-container">
      {/* Breadcrumb */}
      <nav className="delivery-breadcrumb" aria-label="Breadcrumb">
        <span className="delivery-breadcrumb-muted">Overview</span>
        <span className="delivery-breadcrumb-sep" aria-hidden="true">&rsaquo;</span>
        <span className="delivery-breadcrumb-active">My Deliveries</span>
      </nav>

      {/* Header Row */}
      <div className="delivery-header-row">
        <h1 className="delivery-page-title">My Deliveries</h1>
        <ButtonLink to="/customer/create-parcel" className="delivery-create-btn">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" />
            <polyline points="3.27 6.96 12 12.01 20.73 6.96" />
            <line x1="12" y1="22.08" x2="12" y2="12" />
          </svg>
          <span>Create Delivery</span>
        </ButtonLink>
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

      {/* Filters Row */}
      <div className="delivery-filters-row">
        <div className="delivery-search-box">
          <span className="delivery-search-icon" aria-hidden="true">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="11" cy="11" r="8" />
              <line x1="21" y1="21" x2="16.65" y2="16.65" />
            </svg>
          </span>
          <input
            type="search"
            className="delivery-search-input"
            placeholder="Search by tracking number"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value)
              setPage(1)
            }}
            aria-label="Search by tracking number"
          />
        </div>

        <div className="delivery-select-wrapper">
          <select
            className="delivery-filter-select"
            value={statusFilter}
            onChange={(e) => {
              setStatusFilter(e.target.value)
              setPage(1)
            }}
            aria-label="Filter by status"
          >
            <option value="ALL">All statuses</option>
            <option value="OUT_FOR_DELIVERY">Out for Delivery</option>
            <option value="IN_TRANSIT">In Transit</option>
            <option value="DELIVERED">Delivered</option>
            <option value="FAILED">Failed</option>
            <option value="CREATED">Created</option>
            <option value="ASSIGNED">Assigned</option>
            <option value="PICKED_UP">Picked Up</option>
            <option value="CANCELLED">Cancelled</option>
          </select>
          <span className="delivery-select-chevron" aria-hidden="true">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="6 9 12 15 18 9" />
            </svg>
          </span>
        </div>

        <div className="delivery-select-wrapper">
          <select
            className="delivery-filter-select"
            value={dateFilter}
            onChange={(e) => {
              setDateFilter(e.target.value)
              setPage(1)
            }}
            aria-label="Filter by date"
          >
            {DATE_FILTER_OPTIONS.map(({ value, label }) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
          <span className="delivery-select-chevron" aria-hidden="true">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="6 9 12 15 18 9" />
            </svg>
          </span>
        </div>
      </div>

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
          <button type="button" className="btn btn-primary" onClick={loadParcels}>
            Try again
          </button>
        </div>
      ) : (
        /* Main Table Card */
        <section className="delivery-table-card" aria-label="Parcel delivery history">
          <div className="delivery-table-wrapper">
            <table className="delivery-table">
              <thead>
                <tr>
                  <th scope="col">Tracking no.</th>
                  <th scope="col">Destination</th>
                  <th scope="col">Created</th>
                  <th scope="col">Fee</th>
                  <th scope="col">Status</th>
                  <th scope="col" aria-label="Actions"></th>
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
                      badgeClass: 'delivery-badge delivery-badge-cancelled',
                      icon: null,
                    }
                    return (
                      <tr key={parcel.id}>
                        {/* Tracking number */}
                        <td>
                          <span className="delivery-tracking-num">
                            {parcel.trackingNumber ?? '—'}
                          </span>
                        </td>

                        {/* Destination */}
                        <td>
                          <div className="delivery-destination-name">
                            {parcel.deliveryAddress ?? '—'}
                          </div>
                          {parcel.receiverName && (
                            <div className="delivery-destination-sub">
                              {parcel.receiverName}
                            </div>
                          )}
                        </td>

                        {/* Created date */}
                        <td className="delivery-date">
                          {formatDate(parcel.createdAt)}
                        </td>

                        {/* Fee */}
                        <td className="delivery-fee">
                          {parcel.charge != null
                            ? `LKR ${Number(parcel.charge).toLocaleString()}`
                            : '—'}
                        </td>

                        {/* Status badge */}
                        <td>
                          <span className={cfg.badgeClass}>
                            {cfg.icon}
                            <span>{cfg.label}</span>
                          </span>
                        </td>

                        {/* Actions */}
                        <td>
                          <div className="delivery-actions-cell">
                            {parcel.status === 'CREATED' && (
                              <button
                                type="button"
                                className="delivery-btn-cancel"
                                disabled={!!cancelling[parcel.id]}
                                onClick={() => handleCancel(parcel)}
                              >
                                {cancelling[parcel.id] ? 'Cancelling…' : 'Cancel'}
                              </button>
                            )}
                            {parcel.status === 'DELIVERED' && (
                              <button
                                type="button"
                                className="delivery-btn-receipt"
                                disabled={!!downloading[parcel.id]}
                                onClick={() => handleDownloadReceipt(parcel)}
                              >
                                {downloading[parcel.id] ? 'Downloading…' : 'Receipt'}
                              </button>
                            )}
                            <Link
                              to={`/customer/deliveries/${parcel.id}`}
                              className="delivery-btn-view-details"
                            >
                              View Details
                            </Link>
                          </div>
                        </td>
                      </tr>
                    )
                  })
                )}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {/* Footer: row count + pagination */}
      {!loading && !error && (
        <div className="delivery-footer">
          <div className="delivery-footer-count">
            {filtered.length === 0
              ? 'Showing 0 of 0'
              : `Showing ${showFrom}–${showTo} of ${filtered.length}`}
          </div>

          <div className="delivery-pagination" role="navigation" aria-label="Pagination">
            <button
              type="button"
              className="delivery-page-nav-btn"
              disabled={page <= 1}
              onClick={() => setPage((p) => p - 1)}
              aria-label="Previous page"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <polyline points="15 18 9 12 15 6" />
              </svg>
            </button>
            <span className="delivery-page-indicator">
              Page {page} of {totalPages}
            </span>
            <button
              type="button"
              className="delivery-page-nav-btn"
              disabled={page >= totalPages}
              onClick={() => setPage((p) => p + 1)}
              aria-label="Next page"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <polyline points="9 18 15 12 9 6" />
              </svg>
            </button>
          </div>
        </div>
      )}

      {/* Floating Ask Assistant Pill */}
      <button
        type="button"
        className="delivery-floating-assistant"
        onClick={() => setAssistantMessage((prev) => !prev)}
        aria-label="Ask assistant"
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#2563eb" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
        </svg>
        <span>Ask assistant</span>
      </button>

      {assistantMessage && (
        <div
          role="dialog"
          aria-label="Assistant helper"
          style={{
            position: 'fixed',
            bottom: '76px',
            right: '28px',
            zIndex: 51,
            background: '#ffffff',
            border: '1px solid #e2e8f0',
            borderRadius: '12px',
            boxShadow: '0 10px 25px rgba(0, 0, 0, 0.1)',
            padding: '16px 20px',
            width: '280px',
            fontSize: '13px',
            color: '#334155',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
            <strong style={{ color: '#0f172a' }}>SwiftDrop Assistant</strong>
            <button
              type="button"
              onClick={() => setAssistantMessage(false)}
              style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94a3b8', fontSize: '16px' }}
              aria-label="Close assistant"
            >
              ✕
            </button>
          </div>
          <p style={{ margin: 0, lineHeight: 1.4 }}>
            Need help tracking a package or booking a new delivery? I'm here to assist you anytime!
          </p>
        </div>
      )}
    </div>
  )
}
