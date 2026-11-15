import { useEffect, useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import LoadingSpinner from '../../components/LoadingSpinner'
import { parcelService } from '../../services/parcelService'
import { merchantService } from '../../services/merchantService'

const PAGE_SIZE = 10

function label(status) {
  const s = String(status || 'UNKNOWN').toUpperCase()
  switch (s) {
    case 'CREATED':
      return 'Created'
    case 'PICKED_UP':
      return 'Picked Up'
    case 'IN_TRANSIT':
      return 'In Transit'
    case 'OUT_FOR_DELIVERY':
      return 'Out for Delivery'
    case 'DELIVERED':
      return 'Delivered'
    case 'FAILED':
      return 'Failed'
    case 'CANCELLED':
      return 'Cancelled'
    default:
      return s.replaceAll('_', ' ')
  }
}

function statusBadgeClass(status) {
  const s = String(status || '').toUpperCase()
  switch (s) {
    case 'DELIVERED':
      return 'badge-pill badge-pill-delivered'
    case 'OUT_FOR_DELIVERY':
      return 'badge-pill badge-pill-out-for-delivery'
    case 'IN_TRANSIT':
      return 'badge-pill badge-pill-in-transit'
    case 'FAILED':
      return 'badge-pill badge-pill-failed'
    case 'CANCELLED':
      return 'badge-pill badge-pill-cancelled'
    case 'PICKED_UP':
      return 'badge-pill badge-pill-picked-up'
    case 'CREATED':
    default:
      return 'badge-pill badge-pill-created'
  }
}

function money(value) {
  const amount = Number(value)
  return Number.isFinite(amount) ? `LKR ${amount.toFixed(2)}` : '—'
}

function extractCity(address) {
  if (!address || typeof address !== 'string') return '—'
  const parts = address.split(',').map((s) => s.trim()).filter(Boolean)
  return parts.length >= 2 ? parts[parts.length - 1] : parts[0] || '—'
}

function formatDate(isoString) {
  if (!isoString) return '—'
  const date = new Date(isoString)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })
}

function formatDateTime(isoString) {
  if (!isoString) return '—'
  const date = new Date(isoString)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleDateString('en-GB', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

function getOrderRef(order) {
  if (order.merchantOrderReference) return order.merchantOrderReference
  if (order.trackingNumber && order.trackingNumber.length >= 4) {
    return `SF-${order.trackingNumber.slice(-4)}`
  }
  return order.id ? `SF-${String(order.id).slice(-4)}` : '—'
}

const STEP_DEFINITIONS = [
  { key: 'CREATED', label: 'Created' },
  { key: 'PICKED_UP', label: 'Picked Up' },
  { key: 'IN_TRANSIT', label: 'In Transit' },
  { key: 'OUT_FOR_DELIVERY', label: 'Out for Delivery' },
  { key: 'DELIVERED', label: 'Delivered' },
]

function getStepStatus(stepKey, currentStatus) {
  const stages = ['CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED']
  const curIdx = stages.indexOf(String(currentStatus || '').toUpperCase())
  const stepIdx = stages.indexOf(stepKey)
  if (curIdx === -1) {
    return stepKey === 'CREATED' ? 'completed' : 'upcoming'
  }
  if (stepIdx < curIdx) return 'completed'
  if (stepIdx === curIdx) return 'current'
  return 'upcoming'
}

export default function MerchantOrdersPage() {
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [reload, setReload] = useState(0)

  const location = useLocation()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const initialStatus = (searchParams.get('status') || 'ALL').toUpperCase()
  const trackingParam = searchParams.get('tracking')
  const fromOverview = location.state?.from === 'overview'

  // Filtering & Search
  const [searchTerm, setSearchTerm] = useState('')
  const [statusFilter, setStatusFilter] = useState(initialStatus)
  const [dateFilter, setDateFilter] = useState('ANY')

  // Selected Order for Details View (Figma Image 2)
  const [selectedOrder, setSelectedOrder] = useState(location.state?.selectedOrder || null)
  const [historyLoading, setHistoryLoading] = useState(false)
  const [historyEvents, setHistoryEvents] = useState([])
  const [merchantProfile, setMerchantProfile] = useState(null)

  useEffect(() => {
    const s = searchParams.get('status')
    if (s) {
      setStatusFilter(s.toUpperCase())
    } else {
      setStatusFilter('ALL')
    }
  }, [searchParams])

  const handleBack = () => {
    if (fromOverview) {
      navigate('/merchant/dashboard')
    } else {
      setSelectedOrder(null)
      const nextParams = new URLSearchParams(searchParams)
      nextParams.delete('tracking')
      setSearchParams(nextParams, { replace: true })
    }
  }

  useEffect(() => {
    merchantService.getMyMerchant()
      .then((data) => setMerchantProfile(data))
      .catch(() => { })
  }, [])

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

  // Load history when an order is selected
  useEffect(() => {
    if (!selectedOrder?.id) {
      setHistoryEvents([])
      return
    }
    setHistoryLoading(true)
    parcelService.getParcelHistory(selectedOrder.id)
      .then((history) => {
        setHistoryEvents(Array.isArray(history) ? history : [])
      })
      .catch(() => setHistoryEvents([]))
      .finally(() => setHistoryLoading(false))
  }, [selectedOrder])

  const orders = Array.isArray(result?.content) ? result.content : []
  const totalPages = Number(result?.totalPages) || 0
  const totalElements = Number(result?.totalElements) || orders.length

  useEffect(() => {
    if (trackingParam && orders.length > 0) {
      const match = orders.find(
        (o) =>
          String(o.trackingNumber || '').toUpperCase() === trackingParam.toUpperCase() ||
          String(o.merchantOrderReference || '').toUpperCase() === trackingParam.toUpperCase() ||
          String(o.id || '') === trackingParam
      )
      if (match) {
        setSelectedOrder((prev) => (prev ? { ...match, ...prev } : match))
      }
    }
  }, [trackingParam, orders])

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

  // Filter orders by search & status
  const filteredOrders = orders.filter((order) => {
    if (statusFilter !== 'ALL') {
      const s = String(order.status || '').toUpperCase()
      if (statusFilter === 'PENDING') {
        if (s !== 'PENDING' && s !== 'CREATED' && s !== 'PICKED_UP') return false
      } else if (statusFilter === 'IN_TRANSIT') {
        if (s !== 'IN_TRANSIT' && s !== 'OUT_FOR_DELIVERY') return false
      } else if (s !== statusFilter) {
        return false
      }
    }

    if (searchTerm.trim()) {
      const q = searchTerm.toLowerCase().trim()
      const tracking = (order.trackingNumber || '').toLowerCase()
      const ref = (order.merchantOrderReference || getOrderRef(order)).toLowerCase()
      const receiver = (order.receiverName || '').toLowerCase()
      const destination = (order.deliveryAddress || '').toLowerCase()
      if (!tracking.includes(q) && !ref.includes(q) && !receiver.includes(q) && !destination.includes(q)) {
        return false
      }
    }

    if (dateFilter !== 'ANY' && order.createdAt) {
      const orderDate = new Date(order.createdAt).getTime()
      const now = Date.now()
      if (dateFilter === 'TODAY' && now - orderDate > 24 * 3600 * 1000) return false
      if (dateFilter === '7DAYS' && now - orderDate > 7 * 24 * 3600 * 1000) return false
      if (dateFilter === '30DAYS' && now - orderDate > 30 * 24 * 3600 * 1000) return false
    }

    return true
  })

  // ORDER DETAILS SCREEN 

  if (selectedOrder) {
    const orderRef = getOrderRef(selectedOrder)
    const bookingDate = formatDateTime(selectedOrder.createdAt)
    const lastUpdate = formatDateTime(selectedOrder.updatedAt || selectedOrder.createdAt)
    const pickupCity = extractCity(selectedOrder.pickupAddress || merchantProfile?.address)
    const weightNum = Number(selectedOrder.weight) || 1.0
    // Parcel responses retain the booked charge, but not its historical breakdown.
    // Never reconstruct that breakdown using guessed or currently published rates.
    const totalCharge = selectedOrder.charge
    const hasCharge = totalCharge !== null && totalCharge !== undefined &&
      String(totalCharge).trim() !== '' && Number.isFinite(Number(totalCharge)) && Number(totalCharge) >= 0

    return (
      <div className="merchant-scope">
        <nav className="page-breadcrumb" aria-label="Breadcrumb">
          <span>Workspace</span><span>/</span>
          <button
            type="button"
            className="tracking-link"
            style={{ fontSize: '13px' }}
            onClick={handleBack}
          >
            {fromOverview ? 'Overview' : 'Orders'}
          </button>
          <span>/</span>
          <strong>{orderRef}</strong>
        </nav>

        {/* Header Row */}
        <div className="order-details-header">
          <div className="order-details-title-row">
            <h1 className="order-details-title">{selectedOrder.trackingNumber}</h1>
            <button
              type="button"
              className="btn-back-to-orders"
              onClick={handleBack}
              aria-label={fromOverview ? 'Back to Overview' : 'Back to Orders'}
            >
              <svg
                width="15"
                height="15"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
              >
                <path d="M19 12H5M12 19l-7-7 7-7" />
              </svg>
              <span>{fromOverview ? 'Back to Overview' : 'Back to Orders'}</span>
            </button>
          </div>
          <div className="order-details-subrow">
            <span className={statusBadgeClass(selectedOrder.status)}>
              {label(selectedOrder.status)}
            </span>
            <span>Order <strong>{orderRef}</strong></span>
            <span>Booked {bookingDate}</span>
          </div>
        </div>

        {/* Live status banner */}
        <div className="live-updates-banner">
          <span className="live-indicator">
            <span className="live-dot" aria-hidden="true" />
            Live updates active
          </span>
          <span className="live-updated-time">Last update {lastUpdate}</span>
        </div>

        {/* Two-Column Grid */}
        <div className="order-details-grid">
          {/* Left Column */}
          <div className="order-col-stack">
            {/* Pickup Card */}
            <div className="detail-card">
              <h2 className="detail-card-title">Pickup</h2>
              <div className="contact-info-block">
                <span className="contact-name">
                  {merchantProfile?.businessName || merchantProfile?.contactName || 'Merchant Store'}
                </span>
                <span className="contact-phone">
                  {merchantProfile?.phone || '—'}
                </span>
                <span className="contact-address">
                  {selectedOrder.pickupAddress || merchantProfile?.address || '—'}
                </span>
              </div>
            </div>

            {/* Receiver Card */}
            <div className="detail-card">
              <h2 className="detail-card-title">Receiver</h2>
              <div className="contact-info-block">
                <span className="contact-name">{selectedOrder.receiverName || selectedOrder.customerName || 'Customer'}</span>
                <span className="contact-phone">{selectedOrder.receiverPhone || '—'}</span>
                <span className="contact-address">{selectedOrder.deliveryAddress || selectedOrder.destination || '—'}</span>
              </div>
            </div>

            {/* Parcel Details Card */}
            <div className="detail-card">
              <h2 className="detail-card-title">Parcel</h2>
              <div className="kv-list">
                <div className="kv-row">
                  <span className="kv-label">Description</span>
                  <span className="kv-value">{selectedOrder.parcelType || 'Standard parcel'}</span>
                </div>
                <div className="kv-row">
                  <span className="kv-label">Weight</span>
                  <span className="kv-value">{weightNum} kg</span>
                </div>
                <div className="kv-row">
                  <span className="kv-label">Dimensions</span>
                  <span className="kv-value">{selectedOrder.size ? `${selectedOrder.size}` : 'Standard'}</span>
                </div>
                <div className="kv-row">
                  <span className="kv-label">Handling note</span>
                  <span className="kv-value">—</span>
                </div>
                <div className="kv-row">
                  <span className="kv-label">Courier</span>
                  <span className="kv-value">Not yet assigned</span>
                </div>
                <div className="kv-row">
                  <span className="kv-label">Source</span>
                  <span className="kv-value">
                    {selectedOrder.merchantOrderReference ? 'Merchant API' : 'Merchant portal'}
                  </span>
                </div>
              </div>
            </div>

            {/* Delivery History Timeline */}
            <div className="detail-card">
              <h2 className="detail-card-title">Delivery history</h2>
              <p className="detail-card-subtitle">Every status change, including earlier attempts.</p>

              {historyLoading ? (
                <div style={{ padding: '16px 0', textAlign: 'center', color: '#64748b' }}>
                  Loading history…
                </div>
              ) : historyEvents.length > 0 ? (
                <div className="order-timeline">
                  {historyEvents.map((evt, idx) => (
                    <div key={evt.id || idx} className="timeline-item">
                      <span className="timeline-dot" aria-hidden="true" />
                      <div className="timeline-header">
                        <span className="timeline-time">{formatDateTime(evt.changedAt)}</span>
                        <span className={statusBadgeClass(evt.newStatus)}>{label(evt.newStatus)}</span>
                      </div>
                      <p className="timeline-desc">{evt.note || 'Status updated'}</p>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="order-timeline">
                  <div className="timeline-item">
                    <span className="timeline-dot" aria-hidden="true" />
                    <div className="timeline-header">
                      <span className="timeline-time">{bookingDate}</span>
                      <span className={statusBadgeClass(selectedOrder.status)}>
                        {label(selectedOrder.status)}
                      </span>
                      <span style={{ fontSize: '13px', color: '#475569' }}>{pickupCity}</span>
                    </div>
                    <p className="timeline-desc">Attempt 1 · by Merchant</p>
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* Right Column */}
          <div className="order-col-stack">
            {/* Webhook Events */}
            <div className="detail-card">
              <h2 className="detail-card-title">Webhook events</h2>
              <p style={{ fontSize: '13px', color: '#64748b', margin: 0 }}>
                No webhook events for this order.
              </p>
            </div>

            {/* Progress Stepper */}
            <div className="detail-card">
              <h2 className="detail-card-title">Progress</h2>
              <div className="stepper-list">
                {STEP_DEFINITIONS.map((step) => {
                  const state = getStepStatus(step.key, selectedOrder.status)
                  return (
                    <div key={step.key} className={`stepper-item ${state}`}>
                      <span className="stepper-node" aria-hidden="true">
                        {state === 'completed' || state === 'current' ? '✓' : '○'}
                      </span>
                      <span className="stepper-label">
                        {step.label} {state === 'current' ? '(current)' : ''}
                      </span>
                      {step.key === 'CREATED' && (
                        <span className="stepper-meta">{bookingDate} · {pickupCity}</span>
                      )}
                    </div>
                  )
                })}
              </div>
            </div>

            {/* Fee Summary */}
            <div className="detail-card">
              <h2 className="detail-card-title">Fee summary</h2>
              <p className="muted">{hasCharge
                ? 'Charge recorded when this shipment was booked. Itemized fees are not available for this order.'
                : 'The recorded delivery charge is currently unavailable.'}</p>
              <div className="fee-total-row">
                <span>Booked delivery fee</span>
                <span>{hasCharge ? money(totalCharge) : 'Unavailable'}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    )
  }


  // ORDERS LIST / TABLE SCREEN

  const displayCount = filteredOrders.length
  const startIndex = totalElements === 0 ? 0 : page * PAGE_SIZE + 1
  const endIndex = Math.min((page + 1) * PAGE_SIZE, totalElements)

  return (
    <div className="merchant-scope">
      <nav className="page-breadcrumb" aria-label="Breadcrumb">
        <span>Workspace</span><span>/</span><strong>Orders</strong>
      </nav>

      <header className="page-header-row">
        <div>
          <h1 className="page-title">Orders</h1>
          <p className="page-subtitle">Track and manage all your deliveries in one place.</p>
        </div>
      </header>

      {/* Filter and Search Bar */}
      <div className="orders-filter-bar">
        <div className="orders-search-box">
          <span className="orders-search-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="11" cy="11" r="8" />
              <line x1="21" y1="21" x2="16.65" y2="16.65" />
            </svg>
          </span>
          <input
            type="search"
            placeholder="Search tracking number or order ref."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            aria-label="Search orders"
          />
        </div>

        <select
          className="orders-filter-select"
          value={statusFilter}
          onChange={(e) => {
            const next = e.target.value
            setStatusFilter(next)
            const nextParams = new URLSearchParams(searchParams)
            if (next === 'ALL') {
              nextParams.delete('status')
            } else {
              nextParams.set('status', next)
            }
            setSearchParams(nextParams, { replace: true })
          }}
          aria-label="Filter by status"
        >
          <option value="ALL">All statuses</option>
          <option value="PENDING">Pending (Awaiting pickup)</option>
          <option value="IN_TRANSIT">In Transit</option>
          <option value="DELIVERED">Delivered</option>
          <option value="CREATED">Created</option>
          <option value="PICKED_UP">Picked Up</option>
          <option value="OUT_FOR_DELIVERY">Out for Delivery</option>
          <option value="FAILED">Failed</option>
          <option value="CANCELLED">Cancelled</option>
        </select>

        <select
          className="orders-filter-select"
          value={dateFilter}
          onChange={(e) => setDateFilter(e.target.value)}
          aria-label="Filter by date"
        >
          <option value="ANY">Any date</option>
          <option value="TODAY">Today</option>
          <option value="7DAYS">Last 7 days</option>
          <option value="30DAYS">Last 30 days</option>
        </select>
      </div>

      {loading ? (
        <div className="card status-card">
          <LoadingSpinner />
          <p role="status">Loading merchant orders…</p>
        </div>
      ) : error ? (
        <div className="card status-card" role="alert">
          <h2 className="card-title">Unable to load merchant orders</h2>
          <p>{error.message || 'Please try again.'}</p>
          <button type="button" className="btn btn-primary" onClick={retry}>
            Try again
          </button>
        </div>
      ) : orders.length === 0 ? (
        <div className="card status-card">
          <h2 className="card-title">No merchant orders yet</h2>
          <p>Orders created through your merchant account will appear here.</p>
        </div>
      ) : (
        <section className="orders-card" aria-label="Merchant parcel orders">
          <div className="orders-table-wrapper">
            <table className="orders-table">
              <thead>
                <tr>
                  <th style={{ width: '13%' }}>Order ref.</th>
                  <th style={{ width: '18%' }}>Tracking no.</th>
                  <th style={{ width: '25%' }}>Destination</th>
                  <th style={{ width: '14%' }}>Created</th>
                  <th style={{ width: '12%' }}>Fee</th>
                  <th style={{ width: '12%' }}>Status</th>
                  <th style={{ width: '6%', textAlign: 'right' }}>Action</th>
                </tr>
              </thead>
              <tbody>
                {filteredOrders.length === 0 ? (
                  <tr>
                    <td colSpan="7" style={{ textAlign: 'center', padding: '36px', color: '#64748b' }}>
                      No orders match your search or filter criteria.
                    </td>
                  </tr>
                ) : (
                  filteredOrders.map((order) => {
                    const orderRef = getOrderRef(order)
                    const city = extractCity(order.deliveryAddress)
                    return (
                      <tr key={order.id}>
                        <td style={{ fontWeight: 600 }}>{orderRef}</td>
                        <td>
                          <button
                            type="button"
                            className="tracking-link"
                            onClick={() => setSelectedOrder(order)}
                          >
                            {order.trackingNumber}
                          </button>
                        </td>
                        <td>
                          <div className="dest-cell">
                            <span className="dest-city">{city}</span>
                            <span className="dest-name">{order.receiverName || '—'}</span>
                          </div>
                        </td>
                        <td>{formatDate(order.createdAt)}</td>
                        <td>{money(order.charge)}</td>
                        <td>
                          <span className={statusBadgeClass(order.status)}>
                            {label(order.status).toUpperCase()}
                          </span>
                        </td>
                        <td style={{ textAlign: 'right' }}>
                          <button
                            type="button"
                            className="btn-details"
                            onClick={() => setSelectedOrder(order)}
                          >
                            Details
                          </button>
                        </td>
                      </tr>
                    )
                  })
                )}
              </tbody>
            </table>
          </div>

          <div className="orders-table-footer">
            <span>
              Showing {startIndex}–{endIndex} of {totalElements}
            </span>
            <div className="orders-pagination">
              <button
                type="button"
                className="orders-page-btn"
                disabled={page === 0}
                onClick={() => loadPage(page - 1)}
                aria-label="Previous"
                title="Previous page"
              >
                ‹
              </button>
              <span>
                Page {page + 1} of {Math.max(totalPages, 1)}
              </span>
              <button
                type="button"
                className="orders-page-btn"
                disabled={page + 1 >= totalPages}
                onClick={() => loadPage(page + 1)}
                aria-label="Next"
                title="Next page"
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
