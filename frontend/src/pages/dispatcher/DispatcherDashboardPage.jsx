import { useCallback, useEffect, useState } from 'react'
import Button from '../../components/Button'
import LoadingSpinner from '../../components/LoadingSpinner'
import { dispatchService } from '../../services/dispatchService'
import { availabilityLabel, EMPTY_PAGE, formatDateTime, statusLabel } from './dispatchFormat'

function Pagination({ result, noun, onPageChange }) {
  return (
    <div className="dispatch-pagination">
      <span>{result.totalElements} {noun}</span>
      <div>
        <Button variant="secondary" disabled={result.page <= 0} onClick={() => onPageChange(result.page - 1)}>
          Previous
        </Button>
        <span>Page {result.page + 1} of {Math.max(result.totalPages, 1)}</span>
        <Button variant="secondary" disabled={result.page + 1 >= result.totalPages}
          onClick={() => onPageChange(result.page + 1)}>
          Next
        </Button>
      </div>
    </div>
  )
}

function WaitingParcelsTable({ parcels, onAssign, onDetails }) {
  if (parcels.length === 0) {
    return <p className="dispatch-empty">No waiting parcels match these filters.</p>
  }
  return (
    <div className="dispatch-table-wrap">
      <table className="dispatch-table">
        <thead>
          <tr>
            <th>Tracking number</th>
            <th>Route</th>
            <th>Size</th>
            <th>Created</th>
            <th><span className="visually-hidden">Actions</span></th>
          </tr>
        </thead>
        <tbody>
          {parcels.map((parcel) => (
            <tr key={parcel.parcelId}>
              <td>
                <button className="dispatch-link-button" onClick={() => onDetails(parcel.parcelId)}>
                  {parcel.trackingNumber}
                </button>
              </td>
              <td>{parcel.pickupArea} → {parcel.dropoffArea}</td>
              <td>{parcel.size}</td>
              <td>{parcel.createdAt ? new Date(parcel.createdAt).toLocaleDateString() : '—'}</td>
              <td>
                <Button variant="secondary" onClick={() => onAssign(parcel)}
                  aria-label={`Assign ${parcel.trackingNumber}`}>
                  Assign
                </Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function ActiveAssignmentsTable({ assignments, onReassign, onCancel, onDetails }) {
  if (assignments.length === 0) {
    return <p className="dispatch-empty">No active assignments match these filters.</p>
  }
  return (
    <div className="dispatch-table-wrap">
      <table className="dispatch-table">
        <thead>
          <tr>
            <th>Tracking number</th>
            <th>Courier</th>
            <th>Parcel status</th>
            <th>Assigned</th>
            <th><span className="visually-hidden">Actions</span></th>
          </tr>
        </thead>
        <tbody>
          {assignments.map((assignment) => {
            const waiting = assignment.parcelStatus === 'CREATED'
            return (
              <tr key={assignment.assignmentId}>
                <td>
                  <button className="dispatch-link-button" onClick={() => onDetails(assignment.parcelId)}>
                    {assignment.trackingNumber}
                  </button>
                </td>
                <td>{assignment.courierName}</td>
                <td>
                  <span className={`dispatch-status status-${assignment.parcelStatus.toLowerCase()}`}>
                    {statusLabel(assignment.parcelStatus)}
                  </span>
                </td>
                <td>{formatDateTime(assignment.assignedAt)}</td>
                <td>
                  <div className="dispatch-row-actions">
                    <Button variant="secondary" disabled={!waiting} onClick={() => onReassign(assignment)}
                      aria-label={`Reassign ${assignment.trackingNumber}`}>
                      Reassign
                    </Button>
                    <Button variant="secondary" disabled={!waiting} onClick={() => onCancel(assignment)}
                      aria-label={`Cancel assignment ${assignment.trackingNumber}`}>
                      Cancel
                    </Button>
                  </div>
                  {!waiting && <small className="dispatch-hint">Picked up — can no longer change</small>}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

function ParcelDetailDialog({ detail, onClose }) {
  return (
    <div className="dispatch-dialog-backdrop">
      <section className="dispatch-dialog dispatch-detail card" role="dialog" aria-modal="true"
        aria-labelledby="parcel-detail-title">
        <button className="dispatch-dialog-close" aria-label="Close parcel details" onClick={onClose}>×</button>
        {detail.error ? (
          <>
            <h2 id="parcel-detail-title">Unable to load parcel details</h2>
            <p className="dispatch-error" role="alert">{detail.error}</p>
          </>
        ) : !detail.parcel ? (
          <>
            <h2 id="parcel-detail-title" className="visually-hidden">Parcel details</h2>
            <LoadingSpinner label="Loading parcel details" />
          </>
        ) : (
          <>
            <h2 id="parcel-detail-title">{detail.parcel.trackingNumber}</h2>
            <p>{detail.parcel.pickupAddress} → {detail.parcel.deliveryAddress}</p>
            <p>Status: <strong>{statusLabel(detail.parcel.status)}</strong></p>
            <h3>Status timeline</h3>
            {detail.history.length === 0 ? (
              <p className="dispatch-empty">No status events received yet.</p>
            ) : (
              <ol className="dispatch-timeline">
                {detail.history.map((item, index) => (
                  <li key={`${item.changedAt}-${index}`}>
                    <strong>{statusLabel(item.newStatus)}</strong>
                    <time>{formatDateTime(item.changedAt)}</time>
                  </li>
                ))}
              </ol>
            )}
            <h3>Assignments</h3>
            {detail.assignments.length === 0 ? (
              <p className="dispatch-empty">Not assigned yet.</p>
            ) : (
              <ol className="dispatch-timeline">
                {detail.assignments.map((item) => (
                  <li key={item.assignmentId}>
                    <strong>{item.courierName}</strong> · {item.state.toLowerCase()}
                    <time>{formatDateTime(item.assignedAt)}</time>
                  </li>
                ))}
              </ol>
            )}
          </>
        )}
      </section>
    </div>
  )
}

export default function DispatcherDashboardPage() {
  const [unassigned, setUnassigned] = useState(EMPTY_PAGE)
  const [assignments, setAssignments] = useState(EMPTY_PAGE)
  const [couriers, setCouriers] = useState([])
  const [activeTab, setActiveTab] = useState('waiting')
  const [unassignedFilters, setUnassignedFilters] = useState({})
  const [assignmentFilters, setAssignmentFilters] = useState({})
  const [unassignedPage, setUnassignedPage] = useState(0)
  const [assignmentPage, setAssignmentPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [detail, setDetail] = useState(null)
  const [dialog, setDialog] = useState(null)
  const [dialogCourier, setDialogCourier] = useState('')
  const [dialogBusy, setDialogBusy] = useState(false)
  const [dialogError, setDialogError] = useState('')

  const refresh = useCallback(async () => {
    try {
      setError('')
      const [unassignedResult, assignmentResult, courierResult] = await Promise.all([
        dispatchService.getUnassigned({ ...unassignedFilters, page: unassignedPage, pageSize: 10 }),
        dispatchService.getAssignments({ ...assignmentFilters, state: 'ACTIVE', page: assignmentPage, pageSize: 10 }),
        dispatchService.getCouriers(),
      ])
      setUnassigned(unassignedResult)
      setAssignments(assignmentResult)
      setCouriers(courierResult)
    } catch (requestError) {
      setError(requestError.message || 'Unable to load dispatch data.')
    } finally {
      setLoading(false)
    }
  }, [assignmentFilters, assignmentPage, unassignedFilters, unassignedPage])

  useEffect(() => {
    const initialRequest = window.setTimeout(refresh, 0)
    const timer = window.setInterval(refresh, 20_000)
    return () => {
      window.clearTimeout(initialRequest)
      window.clearInterval(timer)
    }
  }, [refresh])

  const showDetails = async (parcelId) => {
    setDetail({ parcelId })
    try {
      setDetail(await dispatchService.getParcel(parcelId))
    } catch (requestError) {
      setDetail({ parcelId, error: requestError.message || 'Unable to load parcel details.' })
    }
  }

  const openDialog = (mode, item) => {
    setDialog({ mode, item })
    setDialogCourier('')
    setDialogError('')
  }

  const submitDialog = async (event) => {
    event.preventDefault()
    setDialogBusy(true)
    setDialogError('')
    try {
      if (dialog.mode === 'assign') {
        await dispatchService.assignCourier(dialog.item.parcelId, dialogCourier)
      } else {
        await dispatchService.reassignCourier(dialog.item.parcelId, dialogCourier)
      }
      setNotice(`${dialog.item.trackingNumber} ${dialog.mode === 'assign' ? 'assigned' : 'reassigned'}.`)
      setDialog(null)
      await refresh()
    } catch (requestError) {
      setDialogError(requestError.message || 'Assignment could not be saved.')
    } finally {
      setDialogBusy(false)
    }
  }

  const cancelAssignment = async (assignment) => {
    try {
      setError('')
      await dispatchService.cancelAssignment(assignment.parcelId)
      setNotice(`Assignment for ${assignment.trackingNumber} cancelled.`)
      await refresh()
    } catch (requestError) {
      setError(requestError.message || 'Assignment could not be cancelled.')
    }
  }

  const submitUnassignedFilters = (event) => {
    event.preventDefault()
    setUnassignedPage(0)
    setUnassignedFilters(Object.fromEntries(new FormData(event.currentTarget)))
  }

  const submitAssignmentFilters = (event) => {
    event.preventDefault()
    setAssignmentPage(0)
    setAssignmentFilters(Object.fromEntries(new FormData(event.currentTarget)))
  }

  const currentCourierId = dialog?.mode === 'reassign' ? dialog.item.courierId : null

  return (
    <section className="dispatch-page">
      <header className="dispatch-page-header">
        <div>
          <h1>Dispatch dashboard</h1>
          <p>Assign waiting parcels and manage active assignments.</p>
        </div>
        <Button variant="secondary" onClick={refresh}>Refresh</Button>
      </header>

      {error && <p className="dispatch-error" role="alert">{error}</p>}
      {notice && <p className="dispatch-notice" role="status">{notice}</p>}

      <section className="dispatch-courier-panel card" aria-labelledby="courier-workload-heading">
        <div className="dispatch-section-heading">
          <div>
            <h2 id="courier-workload-heading">Courier workload</h2>
            <p>Active deliveries are assignments that are not yet delivered, failed or cancelled.</p>
          </div>
        </div>
        {loading && couriers.length === 0 ? (
          <div className="dispatch-loading"><LoadingSpinner label="Loading courier workload" /></div>
        ) : couriers.length === 0 ? (
          <p className="dispatch-empty">No courier profiles were returned by courier service.</p>
        ) : (
          <div className="dispatch-courier-grid">
            {couriers.map((courier) => (
              <article className="dispatch-courier-card" key={courier.courierId}>
                <div>
                  <strong>{courier.name}</strong>
                  <span className={courier.available ? 'availability available' : 'availability unavailable'}>
                    {availabilityLabel(courier)}
                  </span>
                </div>
                <span className="workload-count">{courier.activeParcelCount}<small> active</small></span>
              </article>
            ))}
          </div>
        )}
      </section>

      <div className="dispatch-tabs" role="tablist" aria-label="Parcel views">
        <button role="tab" aria-selected={activeTab === 'waiting'} onClick={() => setActiveTab('waiting')}>
          Waiting parcels <span>{unassigned.totalElements}</span>
        </button>
        <button role="tab" aria-selected={activeTab === 'active'} onClick={() => setActiveTab('active')}>
          Active assignments <span>{assignments.totalElements}</span>
        </button>
      </div>

      {activeTab === 'waiting' ? (
        <section className="dispatch-section card">
          <div className="dispatch-section-heading">
            <div>
              <h2>Waiting parcels</h2>
              <p>New parcels that have no courier yet.</p>
            </div>
          </div>
          <form className="dispatch-filters" onSubmit={submitUnassignedFilters}>
            <label>Pickup area<input name="pickupArea" placeholder="Any pickup area" /></label>
            <label>Drop-off area<input name="dropoffArea" placeholder="Any drop-off area" /></label>
            <label>Size<select name="size" defaultValue=""><option value="">All sizes</option><option>SMALL</option><option>MEDIUM</option><option>LARGE</option></select></label>
            <label>From<input type="date" name="from" /></label>
            <label>To<input type="date" name="to" /></label>
            <Button type="submit">Filter parcels</Button>
          </form>
          {loading && unassigned.content.length === 0 ? (
            <div className="dispatch-loading"><LoadingSpinner label="Loading waiting parcels" /></div>
          ) : (
            <>
              <WaitingParcelsTable parcels={unassigned.content} onDetails={showDetails}
                onAssign={(parcel) => openDialog('assign', parcel)} />
              <Pagination result={unassigned} noun="parcels" onPageChange={setUnassignedPage} />
            </>
          )}
        </section>
      ) : (
        <section className="dispatch-section card">
          <div className="dispatch-section-heading">
            <div>
              <h2>Active assignments</h2>
              <p>Reassign or cancel an assignment until the courier picks the parcel up.</p>
            </div>
          </div>
          <form className="dispatch-filters" onSubmit={submitAssignmentFilters}>
            <label>Tracking number<input name="trackingNumber" placeholder="Search tracking number" /></label>
            <label>Courier<select name="courierId" defaultValue=""><option value="">All couriers</option>
              {couriers.map((courier) => <option key={courier.courierId} value={courier.courierId}>{courier.name}</option>)}
            </select></label>
            <Button type="submit">Search</Button>
          </form>
          {loading && assignments.content.length === 0 ? (
            <div className="dispatch-loading"><LoadingSpinner label="Loading assignments" /></div>
          ) : (
            <>
              <ActiveAssignmentsTable assignments={assignments.content} onDetails={showDetails}
                onReassign={(assignment) => openDialog('reassign', assignment)} onCancel={cancelAssignment} />
              <Pagination result={assignments} noun="assignments" onPageChange={setAssignmentPage} />
            </>
          )}
        </section>
      )}

      {dialog && (
        <div className="dispatch-dialog-backdrop">
          <section className="dispatch-dialog card" role="dialog" aria-modal="true"
            aria-labelledby="assignment-dialog-title">
            <h2 id="assignment-dialog-title">
              {dialog.mode === 'assign' ? 'Assign courier' : 'Reassign courier'}
            </h2>
            <p>
              {dialog.item.trackingNumber}
              {dialog.mode === 'reassign' && <> · currently {dialog.item.courierName}</>}
            </p>
            <form onSubmit={submitDialog}>
              <label htmlFor="assignment-courier">Courier</label>
              <select id="assignment-courier" required value={dialogCourier}
                onChange={(event) => setDialogCourier(event.target.value)}>
                <option value="">Select a courier</option>
                {couriers.map((courier) => (
                  <option value={courier.courierId} key={courier.courierId}
                    disabled={!courier.available || courier.courierId === currentCourierId}>
                    {courier.name} · {courier.activeParcelCount} active · {availabilityLabel(courier)}
                  </option>
                ))}
              </select>
              {dialogError && <p className="dispatch-error" role="alert">{dialogError}</p>}
              <div className="dispatch-dialog-actions">
                <Button variant="secondary" onClick={() => setDialog(null)}>Close</Button>
                <Button type="submit" loading={dialogBusy} loadingText="Saving…">
                  {dialog.mode === 'assign' ? 'Assign' : 'Reassign'}
                </Button>
              </div>
            </form>
          </section>
        </div>
      )}

      {detail && <ParcelDetailDialog detail={detail} onClose={() => setDetail(null)} />}
    </section>
  )
}
