import { useEffect, useMemo, useState } from 'react'
import Button from '../../components/Button'
import LoadingSpinner from '../../components/LoadingSpinner'
import { VEHICLE_TYPES } from '../../services/applicationService'
import { courierApplicationService } from '../../services/courierApplicationService'

const PAGE_SIZE = 5

function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  return !Number.isNaN(date.getTime())
    ? date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
    : '—'
}

function vehicleLabel(value) {
  return VEHICLE_TYPES.find((type) => type.value === value)?.label ?? value ?? '—'
}

function matchesDate(submittedAt, filter) {
  if (filter === 'ALL' || !submittedAt) return true
  const itemDate = new Date(submittedAt)
  const now = new Date()
  if (filter === 'TODAY') {
    return itemDate.getFullYear() === now.getFullYear()
      && itemDate.getMonth() === now.getMonth()
      && itemDate.getDate() === now.getDate()
  }
  const days = filter === '7DAYS' ? 7 : 30
  return itemDate >= new Date(now.getTime() - days * 24 * 60 * 60 * 1000)
}

function StatusBadge({ status, long = false }) {
  if (status === 'PENDING') return <span className="badge badge-warning">{long ? 'Pending Review' : 'Pending'}</span>
  if (status === 'APPROVED') return <span className="badge badge-success">Approved</span>
  if (status === 'REJECTED') return <span className="badge badge-error">Rejected</span>
  return <span className="badge">{status ?? '—'}</span>
}

// Admins never create courier accounts. A courier registers and applies; this
// page is where an administrator approves or rejects that application.
export default function CourierApprovalsPage() {
  const [applications, setApplications] = useState([])
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
  const [detail, setDetail] = useState(null)
  const [toApprove, setToApprove] = useState(null)
  const [approving, setApproving] = useState(false)
  const [toReject, setToReject] = useState(null)
  const [rejectReason, setRejectReason] = useState('')
  const [rejecting, setRejecting] = useState(false)
  const [rejectError, setRejectError] = useState(null)

  const handleRetry = () => {
    setLoading(true)
    setError(null)
    courierApplicationService.getAllApplications()
      .then((data) => {
        setApplications(data || [])
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

    courierApplicationService.getAllApplications(undefined, controller.signal)
      .then((data) => {
        if (!ignore) {
          setApplications(data || [])
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

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase()
    return applications.filter((a) => {
      if (statusFilter !== 'ALL' && a.status !== statusFilter) return false
      if (q) {
        const haystack = [a.name, a.phone, a.vehicleRegistrationNumber, a.preferredArea]
        if (!haystack.some((value) => value?.toLowerCase().includes(q))) return false
      }
      return matchesDate(a.submittedAt, dateFilter)
    })
  }, [applications, statusFilter, search, dateFilter])

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const paginated = useMemo(() => {
    const start = (page - 1) * PAGE_SIZE
    return filtered.slice(start, start + PAGE_SIZE)
  }, [filtered, page])

  const handleResetFilters = () => {
    setSearch('')
    setStatusFilter('ALL')
    setDateFilter('ALL')
    setPage(1)
  }

  const replace = (updated) => {
    setApplications((prev) => prev.map((a) => (a.id === updated.id ? updated : a)))
    if (detail?.id === updated.id) setDetail(updated)
  }

  const openReject = (application) => {
    setToReject(application)
    setRejectReason('')
    setRejectError(null)
  }

  const handleApproveConfirm = async () => {
    if (!toApprove) return
    setApproving(true)
    setActionError(null)
    try {
      const updated = await courierApplicationService.approveApplication(toApprove.id)
      replace(updated)
      setActionSuccess(`Courier "${toApprove.name}" has been approved and the login account is active.`)
      setToApprove(null)
    } catch (err) {
      setActionError(err.message || 'Failed to approve courier.')
      setToApprove(null)
    } finally {
      setApproving(false)
    }
  }

  const handleRejectConfirm = async (e) => {
    e.preventDefault()
    if (!toReject) return
    if (!rejectReason.trim()) {
      setRejectError('Rejection reason is required.')
      return
    }

    setRejecting(true)
    setRejectError(null)
    try {
      const updated = await courierApplicationService.rejectApplication(toReject.id, rejectReason.trim())
      replace(updated)
      setActionSuccess(`Courier "${toReject.name}" has been rejected.`)
      setToReject(null)
      setRejectReason('')
    } catch (err) {
      setRejectError(err.message || 'Failed to reject courier.')
    } finally {
      setRejecting(false)
    }
  }

  const count = (status) => applications.filter((a) => a.status === status).length

  return (
    <div className="merchant-scope admin-approvals">
      <nav className="page-breadcrumb" aria-label="Breadcrumb">
        <span>Admin workspace</span>
        <span>/</span>
        <strong>Courier Approvals</strong>
      </nav>

      <header className="page-header-row">
        <div>
          <h1 className="page-title">Courier Applications</h1>
          <p className="page-subtitle">Review courier applications and decide who may deliver for SwiftDrop.</p>
        </div>
      </header>

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
          <p role="status">Loading courier applications…</p>
        </div>
      ) : error ? (
        <div className="card status-card" role="alert" style={{ margin: '30px auto' }}>
          <h2 className="card-title">Unable to load courier applications</h2>
          <p className="card-subtitle">{error.message || 'An error occurred while fetching courier applications.'}</p>
          <Button onClick={handleRetry}>Try again</Button>
        </div>
      ) : (
        <>
          <section aria-label="Application statistics">
            <div className="stats-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))' }}>
              <div className="stat-card stat-card-amber">
                <span className="stat-label">Pending Applications</span>
                <span className="stat-value">{count('PENDING')}</span>
                <span className="stat-subtext">Awaiting review</span>
              </div>

              <div className="stat-card stat-card-green">
                <span className="stat-label">Approved Couriers</span>
                <span className="stat-value">{count('APPROVED')}</span>
                <span className="stat-subtext">Ready to deliver</span>
              </div>

              <div className="stat-card stat-card-red">
                <span className="stat-label">Rejected Applications</span>
                <span className="stat-value">{count('REJECTED')}</span>
                <span className="stat-subtext">Not approved</span>
              </div>
            </div>
          </section>

          <section className="card-table" aria-label="Courier applications list">
            <div className="card-table-header">
              <div className="card-table-title">
                All applications <span className="card-table-subtitle">{filtered.length} total</span>
              </div>
              <span style={{ fontSize: '12px', color: 'var(--color-text-muted)' }}>
                Live data
              </span>
            </div>

            <div className="filter-bar">
              <div className="search-input-wrapper">
                <span className="search-icon" aria-hidden="true">🔍</span>
                <input
                  type="search"
                  className="input"
                  placeholder="Search name, phone, vehicle or area"
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

            <div className="table-wrapper">
              <table className="data-table">
                <thead>
                  <tr>
                    <th scope="col">Courier</th>
                    <th scope="col">Phone</th>
                    <th scope="col">Vehicle</th>
                    <th scope="col">Registration Number</th>
                    <th scope="col">Preferred Area</th>
                    <th scope="col">Submitted Date</th>
                    <th scope="col">Status</th>
                    <th scope="col">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {paginated.length === 0 ? (
                    <tr>
                      <td colSpan="8" className="table-empty-state">
                        <p className="table-empty-title">No applications found</p>
                        <p>There are no courier applications matching the current search or filters.</p>
                      </td>
                    </tr>
                  ) : (
                    paginated.map((application) => (
                      <tr key={application.id}>
                        <td style={{ fontWeight: 600 }}>{application.name}</td>
                        <td>{application.phone}</td>
                        <td>{vehicleLabel(application.vehicleType)}</td>
                        <td>
                          <code style={{ fontSize: '13px' }}>{application.vehicleRegistrationNumber}</code>
                        </td>
                        <td>{application.preferredArea}</td>
                        <td>{formatDate(application.submittedAt)}</td>
                        <td><StatusBadge status={application.status} /></td>
                        <td>
                          <div className="actions-group">
                            <button
                              type="button"
                              className="action-link action-link-primary"
                              onClick={() => setDetail(application)}
                            >
                              View Details
                            </button>

                            {application.status === 'PENDING' && (
                              <>
                                <button
                                  type="button"
                                  className="action-link action-link-success"
                                  onClick={() => setToApprove(application)}
                                >
                                  Approve
                                </button>
                                <button
                                  type="button"
                                  className="action-link action-link-danger"
                                  onClick={() => openReject(application)}
                                >
                                  Reject
                                </button>
                              </>
                            )}
                            {application.status === 'APPROVED' && (
                              <button
                                type="button"
                                className="action-link action-link-primary"
                                onClick={() => setToApprove(application)}
                              >
                                Sync account activation
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>

            <div className="card-table-footer">
              <span>
                Showing {filtered.length === 0 ? 0 : (page - 1) * PAGE_SIZE + 1}–
                {Math.min(page * PAGE_SIZE, filtered.length)} of {filtered.length} applications
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
            Couriers see the decision, and any rejection reason, when they sign in. Only approved couriers can take deliveries.
          </p>
        </>
      )}

      {/* Modal 1: View Details */}
      {detail && (
        <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="detail-modal-title">
          <div className="modal-dialog">
            <div className="modal-header">
              <div>
                <h2 id="detail-modal-title" className="modal-title">{detail.name}</h2>
                <p className="modal-subtitle">Application Reference: #{detail.id}</p>
              </div>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setDetail(null)}
                aria-label="Close dialog"
              >
                ✕
              </button>
            </div>

            <div className="modal-body">
              <dl className="profile-details" style={{ margin: 0, maxWidth: 'none', border: 'none', padding: 0 }}>
                <dt>Name</dt>
                <dd>{detail.name}</dd>

                <dt>Phone</dt>
                <dd>{detail.phone || '—'}</dd>

                <dt>Vehicle Type</dt>
                <dd>{vehicleLabel(detail.vehicleType)}</dd>

                <dt>Registration Number</dt>
                <dd>{detail.vehicleRegistrationNumber}</dd>

                <dt>Preferred Area</dt>
                <dd>{detail.preferredArea}</dd>

                <dt>Submitted Date</dt>
                <dd>{formatDate(detail.submittedAt)}</dd>

                <dt>Current Status</dt>
                <dd><StatusBadge status={detail.status} long /></dd>

                {detail.reviewedAt && (
                  <>
                    <dt>Reviewed Date</dt>
                    <dd>{formatDate(detail.reviewedAt)}</dd>
                  </>
                )}

                {detail.reviewNote && (
                  <>
                    <dt>Rejection Reason</dt>
                    <dd style={{ color: 'var(--color-error)' }}>{detail.reviewNote}</dd>
                  </>
                )}
              </dl>
            </div>

            <div className="modal-footer">
              <Button variant="secondary" onClick={() => setDetail(null)}>
                Close
              </Button>
              {detail.status === 'PENDING' && (
                <>
                  <Button
                    type="button"
                    onClick={() => openReject(detail)}
                    style={{ backgroundColor: '#dc2626', borderColor: '#dc2626' }}
                  >
                    Reject
                  </Button>
                  <Button
                    type="button"
                    onClick={() => setToApprove(detail)}
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
      {toApprove && (
        <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="approve-modal-title">
          <div className="modal-dialog">
            <div className="modal-header">
              <div>
                <h2 id="approve-modal-title" className="modal-title">
                  {toApprove.status === 'APPROVED' ? 'Sync account activation' : 'Approve Courier Application'}
                </h2>
                <p className="modal-subtitle">{toApprove.name}</p>
              </div>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setToApprove(null)}
                aria-label="Close dialog"
              >
                ✕
              </button>
            </div>

            <div className="modal-body">
              <p style={{ margin: 0 }}>
                Are you sure you want to approve <strong>{toApprove.name}</strong> (
                {vehicleLabel(toApprove.vehicleType)}, <code>{toApprove.vehicleRegistrationNumber}</code>)?
              </p>
              <div className="modal-notice-box" style={{ backgroundColor: '#f0fdf4', borderColor: '#bbf7d0', color: '#166534' }}>
                This activates the courier&apos;s login account and creates their courier profile. The courier must sign in again to start delivering.
              </div>
            </div>

            <div className="modal-footer">
              <Button variant="secondary" onClick={() => setToApprove(null)}>
                Cancel
              </Button>
              <Button
                type="button"
                disabled={approving}
                onClick={handleApproveConfirm}
                style={{ backgroundColor: '#16a34a', borderColor: '#16a34a' }}
              >
                {approving ? 'Confirming…' : toApprove.status === 'APPROVED' ? 'Confirm activation' : 'Approve Application'}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Modal 3: Reject Confirmation */}
      {toReject && (
        <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="reject-modal-title">
          <div className="modal-dialog">
            <form onSubmit={handleRejectConfirm} noValidate>
              <div className="modal-header">
                <div>
                  <h2 id="reject-modal-title" className="modal-title">Reject Courier Application</h2>
                  <p className="modal-subtitle">{toReject.name}</p>
                </div>
                <button
                  type="button"
                  className="modal-close-btn"
                  onClick={() => setToReject(null)}
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
                  Please state the reason for rejecting <strong>{toReject.name}</strong>. This explanation will be recorded and shown to the applicant.
                </p>

                <div className="form-field">
                  <label htmlFor="reject-reason" className="form-label">
                    Rejection Reason
                  </label>
                  <textarea
                    id="reject-reason"
                    className="input"
                    rows={4}
                    maxLength={500}
                    style={{ height: 'auto', padding: '8px 12px', resize: 'vertical' }}
                    placeholder="e.g. Vehicle registration could not be verified..."
                    value={rejectReason}
                    onChange={(e) => setRejectReason(e.target.value)}
                    required
                    autoFocus
                  />
                </div>
              </div>

              <div className="modal-footer">
                <Button variant="secondary" type="button" onClick={() => setToReject(null)}>
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
