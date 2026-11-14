import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import Button from '../../components/Button'
import LoadingSpinner from '../../components/LoadingSpinner'
import { useAuth } from '../../context/AuthContext'
import { merchantService } from '../../services/merchantService'

function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  return !Number.isNaN(date.getTime())
    ? date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
    : '—'
}

function formatDateTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  return !Number.isNaN(date.getTime())
    ? date.toLocaleString(undefined, {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    : '—'
}

function renderStatusBadge(status) {
  const s = String(status || '').toUpperCase()
  if (s === 'DELIVERED') {
    return <span className="badge badge-success">Delivered</span>
  }
  if (s === 'IN_TRANSIT' || s === 'DISPATCHED' || s === 'PICKED_UP') {
    return <span className="badge badge-info">In transit</span>
  }
  if (s === 'PENDING' || s === 'REGISTERED') {
    return <span className="badge badge-warning">Pending</span>
  }
  if (s === 'CANCELLED' || s === 'FAILED' || s === 'RETURNED') {
    return <span className="badge badge-error">{status}</span>
  }
  return <span className="badge badge-muted">{status || 'Unknown'}</span>
}

export default function MerchantDashboardPage() {
  const { user } = useAuth()
  const [merchant, setMerchant] = useState(null)
  const [dashboardData, setDashboardData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [showProfileModal, setShowProfileModal] = useState(false)
  const [selectedOrder, setSelectedOrder] = useState(null)
  const [dateRange, setDateRange] = useState('Nov 1 – Nov 12, 2026')
  const [showDateDropdown, setShowDateDropdown] = useState(false)

  const handleRetry = () => {
    setLoading(true)
    setError(null)
    merchantService.getMyMerchant()
      .then((data) => {
        setMerchant(data)
        if (data?.status === 'APPROVED') {
          return merchantService.getMyDashboard()
            .then((dash) => {
              setDashboardData(dash)
              setLoading(false)
            })
            .catch(() => {
              setDashboardData(null)
              setLoading(false)
            })
        }
        setDashboardData(null)
        setLoading(false)
      })
      .catch((err) => {
        setError(err)
        setLoading(false)
      })
  }

  useEffect(() => {
    let ignore = false
    const controller = new AbortController()

    merchantService.getMyMerchant(controller.signal)
      .then((data) => {
        if (!ignore) {
          setMerchant(data)
          if (data?.status === 'APPROVED') {
            merchantService.getMyDashboard(controller.signal)
              .then((dash) => {
                if (!ignore) {
                  setDashboardData(dash)
                  setLoading(false)
                }
              })
              .catch(() => {
                if (!ignore) {
                  setDashboardData(null)
                  setLoading(false)
                }
              })
          } else {
            setDashboardData(null)
            setLoading(false)
          }
        }
      })
      .catch((err) => {
        if (!ignore && err?.name !== 'AbortError') {
          setError(err)
          setLoading(false)
        }
      })

    return () => {
      ignore = true
      controller.abort()
    }
  }, [])

  const isApproved = merchant?.status === 'APPROVED'
  const isPending = merchant?.status === 'PENDING'
  const isRejected = merchant?.status === 'REJECTED'
  const merchantIdFormatted = merchant?.id ? `MRC-${String(merchant.id).padStart(6, '0')}` : 'MRC-000000'

  return (
    <div className="merchant-scope merchant-dashboard">
      <nav className="page-breadcrumb" aria-label="Breadcrumb">
        <span>Workspace</span>
        <span>/</span>
        <strong>Overview</strong>
      </nav>

      <header className="page-header-row">
        <div>
          <h1 className="page-title">Your business, at a glance</h1>
          <p className="page-subtitle">
            Welcome back, {merchant?.contactName || user?.name || 'Merchant'}. Here's what's happening with your deliveries.
          </p>
        </div>

        <div className="page-header-actions" style={{ position: 'relative' }}>
          <button
            type="button"
            className="date-filter-pill"
            onClick={() => setShowDateDropdown((prev) => !prev)}
            aria-expanded={showDateDropdown}
            aria-label="Filter date range"
          >
            <span>{dateRange}</span>
            <span aria-hidden="true">▾</span>
          </button>
          {showDateDropdown && (
            <div
              className="card"
              style={{
                position: 'absolute',
                top: '100%',
                right: 0,
                marginTop: '4px',
                zIndex: 20,
                minWidth: '180px',
                boxShadow: '0 4px 6px -1px rgba(0,0,0,0.1)',
                padding: '4px 0',
              }}
            >
              {['Nov 1 – Nov 12, 2026', 'Last 7 days', 'Last 30 days', 'All time'].map((range) => (
                <button
                  key={range}
                  type="button"
                  style={{
                    display: 'block',
                    width: '100%',
                    textAlign: 'left',
                    padding: '8px 16px',
                    background: 'none',
                    border: 'none',
                    cursor: 'pointer',
                    fontSize: '13px',
                    color: range === dateRange ? 'var(--color-primary)' : 'var(--color-text)',
                    fontWeight: range === dateRange ? '600' : '400',
                  }}
                  onClick={() => {
                    setDateRange(range)
                    setShowDateDropdown(false)
                  }}
                >
                  {range}
                </button>
              ))}
            </div>
          )}
        </div>
      </header>

      {loading ? (
        <div className="card status-card status-card-loading" style={{ margin: '30px auto' }}>
          <LoadingSpinner />
          <p role="status">Loading merchant overview…</p>
        </div>
      ) : error ? (
        <div className="card status-card" role="alert" style={{ margin: '30px auto' }}>
          <h2 className="card-title">Unable to load merchant overview</h2>
          <p className="card-subtitle">{error.message || 'An error occurred while fetching your merchant details.'}</p>
          <Button onClick={handleRetry}>Try again</Button>
        </div>
      ) : (
        <>
          {/* Merchant Status Alerts */}
          {isPending && (
            <div className="info-banner info-banner-warning" role="status">
              <span className="info-banner-icon" aria-hidden="true">⏳</span>
              <div className="info-banner-content">
                <strong>Merchant application pending review</strong>
                Your application is being reviewed by our administration team. Parcel delivery and API features will be activated upon approval.
              </div>
            </div>
          )}

          {isRejected && (
            <div className="info-banner info-banner-error" role="alert">
              <span className="info-banner-icon" aria-hidden="true">✕</span>
              <div className="info-banner-content">
                <strong>Merchant application rejected</strong>
                {merchant?.rejectionReason
                  ? `Reason: ${merchant.rejectionReason}`
                  : 'Your business application was not approved. Please contact support for more details.'}
              </div>
            </div>
          )}

          {/* Business Details Card */}
          <section className="business-profile-card" aria-label="Business details">
            <div className="business-profile-main">
              <div className="business-profile-title-row">
                <span className="business-profile-name">{merchant?.businessName || 'Business Profile'}</span>
                {isApproved && <span className="badge badge-success">Approved</span>}
                {isPending && <span className="badge badge-warning">Pending</span>}
                {isRejected && <span className="badge badge-error">Rejected</span>}
              </div>
              <p className="business-profile-meta">
                Merchant ID: {merchantIdFormatted} · {merchant?.email} · {merchant?.address || 'Address pending'}
              </p>
            </div>
            <Button variant="secondary" onClick={() => setShowProfileModal(true)}>
              View business profile
            </Button>
          </section>

          {/* Order Statistics Grid */}
          <section aria-label="Order statistics">
            <div className="stats-grid">
              <div className="stat-card stat-card-blue">
                <span className="stat-label">Total Orders</span>
                <span className="stat-value">
                  {dashboardData != null ? dashboardData.totalOrders.toLocaleString() : '—'}
                </span>
                <span className="stat-subtext">
                  {dashboardData != null
                    ? `${dashboardData.totalOrders} total registered deliveries`
                    : 'Unavailable (Parcel Service pending)'}
                </span>
              </div>

              <div className="stat-card stat-card-amber">
                <span className="stat-label">Pending Orders</span>
                <span className="stat-value">
                  {dashboardData != null ? dashboardData.pendingOrders.toLocaleString() : '—'}
                </span>
                <span className="stat-subtext">Awaiting pickup</span>
              </div>

              <div className="stat-card stat-card-blue">
                <span className="stat-label">In Transit Orders</span>
                <span className="stat-value">
                  {dashboardData != null ? dashboardData.inTransitOrders.toLocaleString() : '—'}
                </span>
                <span className="stat-subtext">On the way to customers</span>
              </div>

              <div className="stat-card stat-card-green">
                <span className="stat-label">Delivered Orders</span>
                <span className="stat-value">
                  {dashboardData != null ? dashboardData.deliveredOrders.toLocaleString() : '—'}
                </span>
                <span className="stat-subtext">
                  {dashboardData != null && dashboardData.totalOrders > 0
                    ? `${Math.round((dashboardData.deliveredOrders / dashboardData.totalOrders) * 100)}% of all orders`
                    : 'Delivered'}
                </span>
              </div>
            </div>
          </section>

          {/* Recent Orders Section */}
          <section className="card-table" aria-label="Recent orders">
            <div className="card-table-header">
              <div className="card-table-title">
                Recent orders <span className="card-table-subtitle">Latest {dashboardData?.recentOrders?.length || 4} orders</span>
              </div>
              <span style={{ fontSize: '13px', color: 'var(--color-text-muted)' }}>
                View all orders →
              </span>
            </div>

            <div className="table-wrapper">
              <table className="data-table">
                <thead>
                  <tr>
                    <th scope="col">Tracking ID</th>
                    <th scope="col">Customer</th>
                    <th scope="col">Destination</th>
                    <th scope="col">Status</th>
                    <th scope="col">Date</th>
                  </tr>
                </thead>
                <tbody>
                  {dashboardData?.recentOrders?.length > 0 ? (
                    dashboardData.recentOrders.map((order) => (
                      <tr key={order.id || order.trackingNumber}>
                        <td>
                          <button
                            type="button"
                            className="action-link action-link-primary"
                            style={{
                              background: 'none',
                              border: 'none',
                              padding: 0,
                              fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
                              fontSize: '13px',
                              fontWeight: 600,
                              cursor: 'pointer',
                              textAlign: 'left',
                            }}
                            onClick={() => setSelectedOrder(order)}
                            title="Click to view status history"
                          >
                            {order.trackingNumber}
                          </button>
                        </td>
                        <td>{order.customerName || '—'}</td>
                        <td>{order.destination || '—'}</td>
                        <td>{renderStatusBadge(order.status)}</td>
                        <td>{formatDate(order.createdAt)}</td>
                      </tr>
                    ))
                  ) : dashboardData != null ? (
                    <tr>
                      <td colSpan="5" className="table-empty-state">
                        <p className="table-empty-title">No orders found</p>
                        <p>You haven't placed any parcel orders yet. Use our API to create your first order.</p>
                      </td>
                    </tr>
                  ) : (
                    <tr>
                      <td colSpan="5" className="table-empty-state">
                        <p className="table-empty-title">Order data currently unavailable</p>
                        <p>Order statistics and real-time delivery tracking will appear here once the Parcel Service integration is ready.</p>
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </section>

          {/* Built for your workflow banner */}
          <section className="workflow-card">
            <div className="workflow-info">
              <h3>Built for your workflow</h3>
              <p>Connect your store and keep every delivery in sync.</p>
            </div>
            <div className="workflow-links">
              <Link to="/merchant/api-keys" className="workflow-link">
                Manage API keys →
              </Link>
              <Link to="/merchant/webhooks" className="workflow-link">
                Webhook settings →
              </Link>
            </div>
          </section>

          {/* Business Profile Modal */}
          {showProfileModal && (
            <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="modal-profile-title">
              <div className="modal-dialog">
                <div className="modal-header">
                  <div>
                    <h2 id="modal-profile-title" className="modal-title">{merchant?.businessName || 'Business Profile'}</h2>
                    <p className="modal-subtitle">Registered merchant business information</p>
                  </div>
                  <button
                    type="button"
                    className="modal-close-btn"
                    onClick={() => setShowProfileModal(false)}
                    aria-label="Close dialog"
                  >
                    ✕
                  </button>
                </div>
                <div className="modal-body">
                  <dl className="profile-details" style={{ margin: 0, maxWidth: 'none', border: 'none', padding: 0 }}>
                    <dt>Business Name</dt>
                    <dd>{merchant?.businessName || '—'}</dd>

                    <dt>Registration Number</dt>
                    <dd>{merchant?.registrationNumber || '—'}</dd>

                    <dt>Contact Person</dt>
                    <dd>{merchant?.contactName || '—'}</dd>

                    <dt>Email</dt>
                    <dd>{merchant?.email || '—'}</dd>

                    <dt>Phone</dt>
                    <dd>{merchant?.phone || '—'}</dd>

                    <dt>Business Address</dt>
                    <dd>{merchant?.address || '—'}</dd>

                    <dt>Approval Status</dt>
                    <dd>
                      {isApproved && <span className="badge badge-success">Approved</span>}
                      {isPending && <span className="badge badge-warning">Pending Review</span>}
                      {isRejected && <span className="badge badge-error">Rejected</span>}
                    </dd>

                    {merchant?.rejectionReason && (
                      <>
                        <dt>Rejection Reason</dt>
                        <dd style={{ color: 'var(--color-error)' }}>{merchant.rejectionReason}</dd>
                      </>
                    )}
                  </dl>
                </div>
                <div className="modal-footer">
                  <Button variant="secondary" onClick={() => setShowProfileModal(false)}>
                    Close
                  </Button>
                </div>
              </div>
            </div>
          )}

          {/* Order Status History Modal */}
          {selectedOrder && (
            <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="modal-order-history-title">
              <div className="modal-dialog" style={{ maxWidth: '540px' }}>
                <div className="modal-header">
                  <div>
                    <h2 id="modal-order-history-title" className="modal-title">Order Status History</h2>
                    <p className="modal-subtitle">
                      {selectedOrder.trackingNumber} · {selectedOrder.customerName || 'Customer'}
                    </p>
                  </div>
                  <button
                    type="button"
                    className="modal-close-btn"
                    onClick={() => setSelectedOrder(null)}
                    aria-label="Close dialog"
                  >
                    ✕
                  </button>
                </div>
                <div className="modal-body">
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '12px 16px', background: '#f8fafc', borderRadius: '6px', border: '1px solid var(--color-border)' }}>
                    <div>
                      <span style={{ fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--color-text-muted)', display: 'block' }}>Destination</span>
                      <strong style={{ fontSize: '13px', color: 'var(--color-text)' }}>{selectedOrder.destination || '—'}</strong>
                    </div>
                    <div>
                      <span style={{ fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--color-text-muted)', display: 'block' }}>Current Status</span>
                      {renderStatusBadge(selectedOrder.status)}
                    </div>
                  </div>

                  <div className="order-timeline" aria-label="Status history timeline">
                    {selectedOrder.statusHistory && selectedOrder.statusHistory.length > 0 ? (
                      selectedOrder.statusHistory.map((item, idx) => (
                        <div key={idx} className="timeline-item">
                          <div className="timeline-dot" />
                          <div className="timeline-header">
                            <span className="timeline-title">{item.status}</span>
                            <span className="timeline-time">{formatDateTime(item.timestamp)}</span>
                          </div>
                          {item.description && <p className="timeline-desc">{item.description}</p>}
                        </div>
                      ))
                    ) : (
                      <div className="timeline-item">
                        <div className="timeline-dot" />
                        <div className="timeline-header">
                          <span className="timeline-title">{selectedOrder.status || 'CREATED'}</span>
                          <span className="timeline-time">{formatDateTime(selectedOrder.createdAt)}</span>
                        </div>
                        <p className="timeline-desc">Parcel registered in the system. Additional milestone updates will appear as the courier processes the parcel.</p>
                      </div>
                    )}
                  </div>
                </div>
                <div className="modal-footer">
                  <Button variant="secondary" onClick={() => setSelectedOrder(null)}>
                    Close
                  </Button>
                </div>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}

