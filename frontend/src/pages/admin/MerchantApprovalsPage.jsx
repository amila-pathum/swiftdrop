import { useEffect, useMemo, useState } from 'react'
import Button from '../../components/Button'
import FormField from '../../components/FormField'
import LoadingSpinner from '../../components/LoadingSpinner'
import { merchantService } from '../../services/merchantService'

function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  return !Number.isNaN(date.getTime())
    ? date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
    : '—'
}

const PAGE_SIZE = 5

export default function MerchantApprovalsPage() {
  const [merchants, setMerchants] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  // Filters
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [dateFilter, setDateFilter] = useState('ALL')
  const [page, setPage] = useState(1)

  // Feedback notifications
  const [actionSuccess, setActionSuccess] = useState('')
  const [actionError, setActionError] = useState(null)

  // Modals
  const [detailMerchant, setDetailMerchant] = useState(null)
  const [merchantToApprove, setMerchantToApprove] = useState(null)
  const [approving, setApproving] = useState(false)

  const [merchantToReject, setMerchantToReject] = useState(null)
  const [rejectReason, setRejectReason] = useState('')
  const [rejecting, setRejecting] = useState(false)
  const [rejectError, setRejectError] = useState(null)

  const handleRetry = () => {
    setLoading(true)
    setError(null)
    merchantService.getAllMerchants()
      .then((data) => {
        setMerchants(data || [])
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

    merchantService.getAllMerchants(undefined, controller.signal)
      .then((data) => {
        if (!ignore) {
          setMerchants(data || [])
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

  // Filtered & Search calculations
  const filteredMerchants = useMemo(() => {
    return merchants.filter((m) => {
      // Status filter
      if (statusFilter !== 'ALL' && m.status !== statusFilter) {
        return false
      }

      // Search query
      if (search.trim()) {
        const q = search.trim().toLowerCase()
        const matchesName = m.businessName?.toLowerCase().includes(q)
        const matchesEmail = m.email?.toLowerCase().includes(q)
        const matchesContact = m.contactName?.toLowerCase().includes(q)
        const matchesReg = m.registrationNumber?.toLowerCase().includes(q)
        if (!matchesName && !matchesEmail && !matchesContact && !matchesReg) {
          return false
        }
      }

      // Date filter
      if (dateFilter !== 'ALL' && m.createdAt) {
        const itemDate = new Date(m.createdAt)
        const now = new Date()
        if (dateFilter === 'TODAY') {
          const isSameDay =
            itemDate.getFullYear() === now.getFullYear() &&
            itemDate.getMonth() === now.getMonth() &&
            itemDate.getDate() === now.getDate()
          if (!isSameDay) return false
        } else if (dateFilter === '7DAYS') {
          const sevenDaysAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000)
          if (itemDate < sevenDaysAgo) return false
        } else if (dateFilter === '30DAYS') {
          const thirtyDaysAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000)
          if (itemDate < thirtyDaysAgo) return false
        }
      }

      return true
    })
  }, [merchants, statusFilter, search, dateFilter])

  // Pagination
  const totalPages = Math.max(1, Math.ceil(filteredMerchants.length / PAGE_SIZE))
  const paginatedMerchants = useMemo(() => {
    const start = (page - 1) * PAGE_SIZE
    return filteredMerchants.slice(start, start + PAGE_SIZE)
  }, [filteredMerchants, page])

  // Reset filters
  const handleResetFilters = () => {
    setSearch('')
    setStatusFilter('ALL')
    setDateFilter('ALL')
    setPage(1)
  }

  // Approve action
  const handleApproveConfirm = async () => {
    if (!merchantToApprove) return
    setApproving(true)
    setActionError(null)
    try {
      const updated = await merchantService.approveMerchant(merchantToApprove.id)
      setMerchants((prev) => prev.map((m) => (m.id === updated.id ? updated : m)))
      setActionSuccess(`Merchant "${merchantToApprove.businessName}" has been approved and the login account is active.`)
      setMerchantToApprove(null)
      if (detailMerchant?.id === updated.id) {
        setDetailMerchant(updated)
      }
    } catch (err) {
      setActionError(err.message || 'Failed to approve merchant.')
    } finally {
      setApproving(false)
    }
  }

  // Reject action
  const handleRejectConfirm = async (e) => {
    e.preventDefault()
    if (!merchantToReject) return
    if (!rejectReason.trim()) {
      setRejectError('Rejection reason is required.')
      return
    }

    setRejecting(true)
    setRejectError(null)
    try {
      const updated = await merchantService.rejectMerchant(merchantToReject.id, rejectReason.trim())
      setMerchants((prev) => prev.map((m) => (m.id === updated.id ? updated : m)))
      setActionSuccess(`Merchant "${merchantToReject.businessName}" has been rejected.`)
      setMerchantToReject(null)
      setRejectReason('')
      if (detailMerchant?.id === updated.id) {
        setDetailMerchant(updated)
      }
    } catch (err) {
      setRejectError(err.message || 'Failed to reject merchant.')
    } finally {
      setRejecting(false)
    }
  }

  // Stats calculation
  const pendingCount = merchants.filter((m) => m.status === 'PENDING').length
  const approvedCount = merchants.filter((m) => m.status === 'APPROVED').length
  const rejectedCount = merchants.filter((m) => m.status === 'REJECTED').length

  return (
    <div className="merchant-scope admin-approvals">
      <nav className="page-breadcrumb" aria-label="Breadcrumb">
        <span>Admin workspace</span>
        <span>/</span>
        <strong>Merchant Approvals</strong>
      </nav>

      <header className="page-header-row">
        <div>
          <h1 className="page-title">Merchant Applications</h1>
          <p className="page-subtitle">Review business applications and manage merchant access to SwiftDrop.</p>
        </div>
      </header>

      {/* Global Action Notifications */}
      {actionSuccess && (
        <div className="alert alert-success" role="status" style={{ marginBottom: '20px' }}>
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
        <div className="alert alert-error" role="alert" style={{ marginBottom: '20px' }}>
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

      {loading ? (
        <div className="card status-card status-card-loading" style={{ margin: '30px auto' }}>
          <LoadingSpinner />
          <p role="status">Loading merchant applications…</p>
        </div>
      ) : error ? (
        <div className="card status-card" role="alert" style={{ margin: '30px auto' }}>
          <h2 className="card-title">Unable to load merchant applications</h2>
          <p className="card-subtitle">{error.message || 'An error occurred while fetching merchant applications.'}</p>
          <Button onClick={handleRetry}>Try again</Button>
        </div>
      ) : (
        <>
          {/* 3 Stat Cards */}
          <section aria-label="Application statistics">
            <div className="stats-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))' }}>
              <div className="stat-card stat-card-amber">
                <span className="stat-label">Pending Applications</span>
                <span className="stat-value">{pendingCount}</span>
                <span className="stat-subtext">Awaiting review</span>
              </div>

              <div className="stat-card stat-card-green">
                <span className="stat-label">Approved Merchants</span>
                <span className="stat-value">{approvedCount}</span>
                <span className="stat-subtext">Ready to ship</span>
              </div>

              <div className="stat-card stat-card-red">
                <span className="stat-label">Rejected Applications</span>
                <span className="stat-value">{rejectedCount}</span>
                <span className="stat-subtext">Not approved</span>
              </div>
            </div>
          </section>

          {/* Main Table Card */}
          <section className="card-table" aria-label="Merchant applications list">
            <div className="card-table-header">
              <div className="card-table-title">
                All applications <span className="card-table-subtitle">{filteredMerchants.length} total</span>
              </div>
              <span style={{ fontSize: '12px', color: 'var(--color-text-muted)' }}>
                Live data
              </span>
            </div>

            {/* Filter Bar */}
            <div className="filter-bar">
              <div className="search-input-wrapper">
                <span className="search-icon" aria-hidden="true">🔍</span>
                <input
                  type="search"
                  className="input"
                  placeholder="Search business name or email"
                  value={search}
                  onChange={(e) => {
                    setSearch(e.target.value)
                    setPage(1)
                  }}
                  aria-label="Search applications"
                />
              </div>

              <select
                className="filter-select"
                value={statusFilter}
                onChange={(e) => {
                  setStatusFilter(e.target.value)
                  setPage(1)
                }}
                aria-label="Filter by status"
              >
                <option value="ALL">Status: All</option>
                <option value="PENDING">Status: Pending</option>
                <option value="APPROVED">Status: Approved</option>
                <option value="REJECTED">Status: Rejected</option>
              </select>

              <select
                className="filter-select"
                value={dateFilter}
                onChange={(e) => {
                  setDateFilter(e.target.value)
                  setPage(1)
                }}
                aria-label="Filter by submission date"
              >
                <option value="ALL">Submitted: All dates</option>
                <option value="TODAY">Submitted: Today</option>
                <option value="7DAYS">Submitted: Last 7 days</option>
                <option value="30DAYS">Submitted: Last 30 days</option>
              </select>

              <button
                type="button"
                className="action-link action-link-primary"
                onClick={handleResetFilters}
                style={{ marginLeft: '4px' }}
              >
                Reset
              </button>
            </div>

            {/* Applications Data Table */}
            <div className="table-wrapper">
              <table className="data-table">
                <thead>
                  <tr>
                    <th scope="col">Business Name</th>
                    <th scope="col">Registration Number</th>
                    <th scope="col">Contact Person</th>
                    <th scope="col">Email</th>
                    <th scope="col">Submitted Date</th>
                    <th scope="col">Status</th>
                    <th scope="col">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {paginatedMerchants.length === 0 ? (
                    <tr>
                      <td colSpan="7" className="table-empty-state">
                        <p className="table-empty-title">No applications found</p>
                        <p>There are no merchant applications matching the current search or filters.</p>
                      </td>
                    </tr>
                  ) : (
                    paginatedMerchants.map((merchant) => {
                      const isPending = merchant.status === 'PENDING'
                      const isApproved = merchant.status === 'APPROVED'
                      const isRejected = merchant.status === 'REJECTED'

                      return (
                        <tr key={merchant.id}>
                          <td style={{ fontWeight: 600 }}>{merchant.businessName}</td>
                          <td>
                            <code style={{ fontSize: '13px' }}>{merchant.registrationNumber}</code>
                          </td>
                          <td>{merchant.contactName}</td>
                          <td>{merchant.email}</td>
                          <td>{formatDate(merchant.createdAt)}</td>
                          <td>
                            {isPending && <span className="badge badge-warning">Pending</span>}
                            {isApproved && <span className="badge badge-success">Approved</span>}
                            {isRejected && <span className="badge badge-error">Rejected</span>}
                          </td>
                          <td>
                            <div className="actions-group">
                              <button
                                type="button"
                                className="action-link action-link-primary"
                                onClick={() => setDetailMerchant(merchant)}
                              >
                                View Details
                              </button>

                              {isPending && (
                                <>
                                  <button
                                    type="button"
                                    className="action-link action-link-success"
                                    onClick={() => setMerchantToApprove(merchant)}
                                  >
                                    Approve
                                  </button>
                                  <button
                                    type="button"
                                    className="action-link action-link-danger"
                                    onClick={() => {
                                      setMerchantToReject(merchant)
                                      setRejectReason('')
                                      setRejectError(null)
                                    }}
                                  >
                                    Reject
                                  </button>
                                </>
                              )}
                              {isApproved && <button type="button" className="action-link action-link-primary"
                                onClick={() => setMerchantToApprove(merchant)}>Sync account activation</button>}
                            </div>
                          </td>
                        </tr>
                      )
                    })
                  )}
                </tbody>
              </table>
            </div>

            {/* Footer & Pagination */}
            <div className="card-table-footer">
              <span>
                Showing {filteredMerchants.length === 0 ? 0 : (page - 1) * PAGE_SIZE + 1}–
                {Math.min(page * PAGE_SIZE, filteredMerchants.length)} of {filteredMerchants.length} applications
              </span>

              {totalPages > 1 && (
                <div className="pagination" role="navigation" aria-label="Pagination">
                  <button
                    type="button"
                    className="pagination-btn"
                    disabled={page <= 1}
                    onClick={() => setPage((p) => p - 1)}
                    aria-label="Previous page"
                  >
                    ‹ Previous
                  </button>

                  {Array.from({ length: totalPages }, (_, i) => i + 1).map((pageNum) => (
                    <button
                      key={pageNum}
                      type="button"
                      className={`pagination-btn ${pageNum === page ? 'active' : ''}`}
                      onClick={() => setPage(pageNum)}
                      aria-current={pageNum === page ? 'page' : undefined}
                    >
                      {pageNum}
                    </button>
                  ))}

                  <button
                    type="button"
                    className="pagination-btn"
                    disabled={page >= totalPages}
                    onClick={() => setPage((p) => p + 1)}
                    aria-label="Next page"
                  >
                    Next ›
                  </button>
                </div>
              )}
            </div>
          </section>

          <p style={{ fontSize: '13px', color: 'var(--color-text-muted)', margin: '16px 0 0' }}>
            Decisions are emailed to the contact person. Only approved merchants can access shipping and API tools.
          </p>
        </>
      )}

      {/* Modal 1: View Details */}
      {detailMerchant && (
        <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="detail-modal-title">
          <div className="modal-dialog">
            <div className="modal-header">
              <div>
                <h2 id="detail-modal-title" className="modal-title">{detailMerchant.businessName}</h2>
                <p className="modal-subtitle">Application Reference: #{detailMerchant.id}</p>
              </div>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setDetailMerchant(null)}
                aria-label="Close dialog"
              >
                ✕
              </button>
            </div>

            <div className="modal-body">
              <dl className="profile-details" style={{ margin: 0, maxWidth: 'none', border: 'none', padding: 0 }}>
                <dt>Business Name</dt>
                <dd>{detailMerchant.businessName}</dd>

                <dt>Registration Number</dt>
                <dd>{detailMerchant.registrationNumber}</dd>

                <dt>Contact Person</dt>
                <dd>{detailMerchant.contactName}</dd>

                <dt>Email</dt>
                <dd>{detailMerchant.email}</dd>

                <dt>Phone</dt>
                <dd>{detailMerchant.phone || '—'}</dd>

                <dt>Business Address</dt>
                <dd>{detailMerchant.address || '—'}</dd>

                <dt>Submitted Date</dt>
                <dd>{formatDate(detailMerchant.createdAt)}</dd>

                <dt>Current Status</dt>
                <dd>
                  {detailMerchant.status === 'PENDING' && <span className="badge badge-warning">Pending Review</span>}
                  {detailMerchant.status === 'APPROVED' && <span className="badge badge-success">Approved</span>}
                  {detailMerchant.status === 'REJECTED' && <span className="badge badge-error">Rejected</span>}
                </dd>

                {detailMerchant.rejectionReason && (
                  <>
                    <dt>Rejection Reason</dt>
                    <dd style={{ color: 'var(--color-error)' }}>{detailMerchant.rejectionReason}</dd>
                  </>
                )}
              </dl>
            </div>

            <div className="modal-footer">
              <Button variant="secondary" onClick={() => setDetailMerchant(null)}>
                Close
              </Button>
              {detailMerchant.status === 'PENDING' && (
                <>
                  <Button
                    type="button"
                    onClick={() => {
                      setMerchantToReject(detailMerchant)
                      setRejectReason('')
                      setRejectError(null)
                    }}
                    style={{ backgroundColor: '#dc2626', borderColor: '#dc2626' }}
                  >
                    Reject
                  </Button>
                  <Button
                    type="button"
                    onClick={() => setMerchantToApprove(detailMerchant)}
                    style={{ backgroundColor: '#16a34a', borderColor: '#16a34a' }}
                  >
                    Approve
                  </Button>
                </>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Modal 2: Approve Confirmation */}
      {merchantToApprove && (
        <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="approve-modal-title">
          <div className="modal-dialog">
            <div className="modal-header">
              <div>
                <h2 id="approve-modal-title" className="modal-title">{merchantToApprove.status === 'APPROVED' ? 'Sync account activation' : 'Approve Merchant Application'}</h2>
                <p className="modal-subtitle">{merchantToApprove.businessName}</p>
              </div>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setMerchantToApprove(null)}
                aria-label="Close dialog"
              >
                ✕
              </button>
            </div>

            <div className="modal-body">
              <p style={{ margin: 0 }}>
                Are you sure you want to approve <strong>{merchantToApprove.businessName}</strong> (Reg: <code>{merchantToApprove.registrationNumber}</code>)?
              </p>
              <div className="modal-notice-box" style={{ backgroundColor: '#f0fdf4', borderColor: '#bbf7d0', color: '#166534' }}>
                Confirm business approval and activate the linked login account. The merchant must sign in again to use their account.
              </div>
            </div>

            <div className="modal-footer">
              <Button variant="secondary" onClick={() => setMerchantToApprove(null)}>
                Cancel
              </Button>
              <Button
                type="button"
                disabled={approving}
                onClick={handleApproveConfirm}
                style={{ backgroundColor: '#16a34a', borderColor: '#16a34a' }}
              >
                {approving ? 'Confirming…' : merchantToApprove.status === 'APPROVED' ? 'Confirm activation' : 'Approve Application'}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Modal 3: Reject Confirmation */}
      {merchantToReject && (
        <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="reject-modal-title">
          <div className="modal-dialog">
            <form onSubmit={handleRejectConfirm}>
              <div className="modal-header">
                <div>
                  <h2 id="reject-modal-title" className="modal-title">Reject Merchant Application</h2>
                  <p className="modal-subtitle">{merchantToReject.businessName}</p>
                </div>
                <button
                  type="button"
                  className="modal-close-btn"
                  onClick={() => setMerchantToReject(null)}
                  aria-label="Close dialog"
                >
                  ✕
                </button>
              </div>

              <div className="modal-body">
                {rejectError && (
                  <div className="alert alert-error" role="alert">
                    {rejectError}
                  </div>
                )}
                <p style={{ margin: 0 }}>
                  Please state the reason for rejecting <strong>{merchantToReject.businessName}</strong>. This explanation will be recorded and shared with the applicant.
                </p>

                <div className="form-field">
                  <label htmlFor="reject-reason" className="form-label">
                    Rejection Reason
                  </label>
                  <textarea
                    id="reject-reason"
                    className="input"
                    rows={4}
                    style={{ height: 'auto', padding: '8px 12px', resize: 'vertical' }}
                    placeholder="e.g. Incomplete business documentation, invalid registration number..."
                    value={rejectReason}
                    onChange={(e) => setRejectReason(e.target.value)}
                    required
                    autoFocus
                  />
                </div>
              </div>

              <div className="modal-footer">
                <Button variant="secondary" type="button" onClick={() => setMerchantToReject(null)}>
                  Cancel
                </Button>
                <Button
                  type="submit"
                  disabled={rejecting}
                  style={{ backgroundColor: '#dc2626', borderColor: '#dc2626' }}
                >
                  {rejecting ? 'Rejecting…' : 'Confirm Rejection'}
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
