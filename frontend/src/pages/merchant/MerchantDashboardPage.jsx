import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import Button from '../../components/Button'
import LoadingSpinner from '../../components/LoadingSpinner'
import { useAuth } from '../../context/AuthContext'
import { merchantService } from '../../services/merchantService'

export default function MerchantDashboardPage() {
  const { user } = useAuth()
  const [merchant, setMerchant] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [showProfileModal, setShowProfileModal] = useState(false)
  const [dateRange, setDateRange] = useState('Nov 1 – Nov 12, 2026')
  const [showDateDropdown, setShowDateDropdown] = useState(false)

  const handleRetry = () => {
    setLoading(true)
    setError(null)
    merchantService.getMyMerchant()
      .then((data) => {
        setMerchant(data)
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
          setLoading(false)
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
                <span className="stat-value">—</span>
                <span className="stat-subtext">Unavailable (Parcel Service pending)</span>
              </div>

              <div className="stat-card stat-card-amber">
                <span className="stat-label">Pending Orders</span>
                <span className="stat-value">—</span>
                <span className="stat-subtext">Awaiting pickup</span>
              </div>

              <div className="stat-card stat-card-blue">
                <span className="stat-label">In Transit Orders</span>
                <span className="stat-value">—</span>
                <span className="stat-subtext">On the way to customers</span>
              </div>

              <div className="stat-card stat-card-green">
                <span className="stat-label">Delivered Orders</span>
                <span className="stat-value">—</span>
                <span className="stat-subtext">Delivered</span>
              </div>
            </div>
          </section>

          {/* Recent Orders Section */}
          <section className="card-table" aria-label="Recent orders">
            <div className="card-table-header">
              <div className="card-table-title">
                Recent orders <span className="card-table-subtitle">Latest 4 orders</span>
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
                  <tr>
                    <td colSpan="5" className="table-empty-state">
                      <p className="table-empty-title">Order data currently unavailable</p>
                      <p>Order statistics and real-time delivery tracking will appear here once the Parcel Service integration is ready.</p>
                    </td>
                  </tr>
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
        </>
      )}
    </div>
  )
}
