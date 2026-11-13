import { useCallback, useEffect, useState } from 'react'
import Button from '../../components/Button'
import LoadingSpinner from '../../components/LoadingSpinner'
import { dispatchService } from '../../services/dispatchService'

const EMPTY_PAGE = { content: [], page: 0, totalPages: 0, totalElements: 0 }

function ParcelTable({ parcels, onAssign, onDetails, emptyMessage }) {
  if (parcels.length === 0) {
    return <p className="dispatch-empty">{emptyMessage}</p>
  }

  return (
    <div className="dispatch-table-wrap">
      <table className="dispatch-table">
        <thead>
          <tr>
            <th>Tracking number</th>
            <th>Route</th>
            <th>Size</th>
            <th>Status</th>
            <th>Courier</th>
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
              <td><span className={`dispatch-status status-${parcel.status.toLowerCase()}`}>{parcel.status.replaceAll('_', ' ')}</span></td>
              <td>{parcel.courierName || 'Unassigned'}</td>
              <td>{new Date(parcel.createdAt).toLocaleDateString()}</td>
              <td>
                {parcel.status === 'CREATED' && (
                  <Button variant="secondary" onClick={() => onAssign(parcel)}>
                    {parcel.courierId ? 'Reassign' : 'Assign'}
                  </Button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function Pagination({ result, onPageChange }) {
  return (
    <div className="dispatch-pagination">
      <span>{result.totalElements} parcels</span>
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

export default function DispatcherDashboardPage() {
  const [unassigned, setUnassigned] = useState(EMPTY_PAGE)
  const [parcels, setParcels] = useState(EMPTY_PAGE)
  const [couriers, setCouriers] = useState([])
  const [activeTab, setActiveTab] = useState('unassigned')
  const [unassignedFilters, setUnassignedFilters] = useState({})
  const [parcelFilters, setParcelFilters] = useState({})
  const [unassignedPage, setUnassignedPage] = useState(0)
  const [parcelPage, setParcelPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selectedParcel, setSelectedParcel] = useState(null)
  const [assignmentParcel, setAssignmentParcel] = useState(null)
  const [assignmentCourier, setAssignmentCourier] = useState('')
  const [assignmentBusy, setAssignmentBusy] = useState(false)
  const [assignmentError, setAssignmentError] = useState('')
  const selectedParcelId = selectedParcel?.parcelId ?? selectedParcel?.parcel?.parcelId

  const refresh = useCallback(async () => {
    try {
      setError('')
      const [unassignedResult, parcelResult, courierResult] = await Promise.all([
        dispatchService.getUnassigned({ ...unassignedFilters, page: unassignedPage, pageSize: 10 }),
        dispatchService.getParcels({ ...parcelFilters, page: parcelPage, pageSize: 10 }),
        dispatchService.getCouriers(),
      ])
      setUnassigned(unassignedResult)
      setParcels(parcelResult)
      setCouriers(courierResult)
    } catch (requestError) {
      setError(requestError.message || 'Unable to load dispatch data.')
    } finally {
      setLoading(false)
    }
  }, [parcelFilters, parcelPage, unassignedFilters, unassignedPage])

  useEffect(() => {
    const initialRequest = window.setTimeout(refresh, 0)
    const timer = window.setInterval(refresh, 20_000)
    return () => {
      window.clearTimeout(initialRequest)
      window.clearInterval(timer)
    }
  }, [refresh])

  useEffect(() => {
    if (!selectedParcelId) return undefined
    let active = true
    dispatchService.getParcel(selectedParcelId)
      .then((detail) => { if (active) setSelectedParcel(detail) })
      .catch((requestError) => {
        if (active) setSelectedParcel({ parcelId: selectedParcelId, error: requestError.message })
      })
    return () => { active = false }
  }, [selectedParcelId])

  const openAssign = (parcel) => {
    setAssignmentParcel(parcel)
    setAssignmentCourier(parcel.courierId || '')
    setAssignmentError('')
  }

  const submitAssignment = async (event) => {
    event.preventDefault()
    setAssignmentBusy(true)
    setAssignmentError('')
    try {
      await dispatchService.assignCourier(assignmentParcel.parcelId, assignmentCourier)
      setAssignmentParcel(null)
      await refresh()
    } catch (requestError) {
      setAssignmentError(requestError.message || 'Assignment could not be saved.')
    } finally {
      setAssignmentBusy(false)
    }
  }

  const submitUnassignedFilters = (event) => {
    event.preventDefault()
    setUnassignedPage(0)
    setUnassignedFilters(Object.fromEntries(new FormData(event.currentTarget)))
  }

  const submitParcelFilters = (event) => {
    event.preventDefault()
    setParcelPage(0)
    setParcelFilters(Object.fromEntries(new FormData(event.currentTarget)))
  }

  return (
    <section className="dispatch-page">
      <header className="dispatch-page-header">
        <div>
          <h1>Dispatch dashboard</h1>
          <p>Assign parcels and monitor active courier workload.</p>
        </div>
        <Button variant="secondary" onClick={refresh}>Refresh</Button>
      </header>

      {error && <p className="dispatch-error" role="alert">{error}</p>}

      <section className="dispatch-courier-panel card" aria-labelledby="courier-workload-heading">
        <div className="dispatch-section-heading">
          <div>
            <h2 id="courier-workload-heading">Courier workload</h2>
            <p>Active parcels include assigned, picked up, in transit and out for delivery.</p>
          </div>
        </div>
        {loading && couriers.length === 0 ? (
          <div className="dispatch-loading"><LoadingSpinner label="Loading courier workload" /></div>
        ) : couriers.length === 0 ? (
          <p className="dispatch-empty">No courier profiles are available in the dispatch projection.</p>
        ) : (
          <div className="dispatch-courier-grid">
            {couriers.map((courier) => (
              <article className="dispatch-courier-card" key={courier.courierId}>
                <div>
                  <strong>{courier.name}</strong>
                  <span className={courier.available ? 'availability available' : 'availability unavailable'}>
                    {courier.available ? 'Available' : 'Unavailable'}
                  </span>
                </div>
                <span className="workload-count">{courier.activeParcelCount}<small> active parcels</small></span>
              </article>
            ))}
          </div>
        )}
      </section>

      <div className="dispatch-tabs" role="tablist" aria-label="Parcel views">
        <button role="tab" aria-selected={activeTab === 'unassigned'} onClick={() => setActiveTab('unassigned')}>
          Unassigned <span>{unassigned.totalElements}</span>
        </button>
        <button role="tab" aria-selected={activeTab === 'all'} onClick={() => setActiveTab('all')}>
          All parcels <span>{parcels.totalElements}</span>
        </button>
      </div>

      {activeTab === 'unassigned' ? (
        <section className="dispatch-section card">
          <div className="dispatch-section-heading">
            <div>
              <h2>Unassigned parcels</h2>
              <p>Newly created parcels awaiting a courier.</p>
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
            <div className="dispatch-loading"><LoadingSpinner label="Loading unassigned parcels" /></div>
          ) : (
            <>
              <ParcelTable parcels={unassigned.content} onAssign={openAssign}
                onDetails={(id) => setSelectedParcel({ parcelId: id })}
                emptyMessage="No unassigned parcels match these filters." />
              <Pagination result={unassigned} onPageChange={setUnassignedPage} />
            </>
          )}
        </section>
      ) : (
        <section className="dispatch-section card">
          <div className="dispatch-section-heading">
            <div>
              <h2>All parcels</h2>
              <p>Search dispatch records by status, courier, creation date or tracking number.</p>
            </div>
          </div>
          <form className="dispatch-filters" onSubmit={submitParcelFilters}>
            <label>Tracking number<input name="trackingNumber" placeholder="Search tracking number" /></label>
            <label>Status<select name="status" defaultValue=""><option value="">All statuses</option>
              {['CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED'].map((status) =>
                <option key={status}>{status}</option>)}
            </select></label>
            <label>Courier<select name="courierId" defaultValue=""><option value="">All couriers</option>
              {couriers.map((courier) => <option key={courier.courierId} value={courier.courierId}>{courier.name}</option>)}
            </select></label>
            <label>From<input type="date" name="from" /></label>
            <label>To<input type="date" name="to" /></label>
            <Button type="submit">Search</Button>
          </form>
          {loading && parcels.content.length === 0 ? (
            <div className="dispatch-loading"><LoadingSpinner label="Loading parcels" /></div>
          ) : (
            <>
              <ParcelTable parcels={parcels.content} onAssign={openAssign}
                onDetails={(id) => setSelectedParcel({ parcelId: id })}
                emptyMessage="No parcels match these filters." />
              <Pagination result={parcels} onPageChange={setParcelPage} />
            </>
          )}
        </section>
      )}

      {assignmentParcel && (
        <div className="dispatch-dialog-backdrop">
          <section className="dispatch-dialog card" role="dialog" aria-modal="true"
            aria-labelledby="assignment-dialog-title">
            <h2 id="assignment-dialog-title">
              {assignmentParcel.courierId ? 'Reassign courier' : 'Assign courier'}
            </h2>
            <p>{assignmentParcel.trackingNumber}</p>
            <form onSubmit={submitAssignment}>
              <label htmlFor="assignment-courier">Courier</label>
              <select id="assignment-courier" required value={assignmentCourier}
                onChange={(event) => setAssignmentCourier(event.target.value)}>
                <option value="">Select a courier</option>
                {couriers.map((courier) => (
                  <option value={courier.courierId} key={courier.courierId}>
                    {courier.name} · {courier.activeParcelCount} active · {courier.available ? 'Available' : 'Unavailable'}
                  </option>
                ))}
              </select>
              {assignmentError && <p className="dispatch-error" role="alert">{assignmentError}</p>}
              <div className="dispatch-dialog-actions">
                <Button variant="secondary" onClick={() => setAssignmentParcel(null)}>Cancel</Button>
                <Button type="submit" loading={assignmentBusy} loadingText="Saving…">
                  Save assignment
                </Button>
              </div>
            </form>
          </section>
        </div>
      )}

      {selectedParcel?.history && (
        <div className="dispatch-dialog-backdrop">
          <section className="dispatch-dialog dispatch-detail card" role="dialog" aria-modal="true"
            aria-labelledby="parcel-detail-title">
            <button className="dispatch-dialog-close" aria-label="Close parcel details"
              onClick={() => setSelectedParcel(null)}>×</button>
            <h2 id="parcel-detail-title">{selectedParcel.parcel.trackingNumber}</h2>
            <p>{selectedParcel.parcel.pickupAddress} → {selectedParcel.parcel.deliveryAddress}</p>
            <p>Status: <strong>{selectedParcel.parcel.status.replaceAll('_', ' ')}</strong></p>
            <h3>Status timeline</h3>
            {selectedParcel.history.length === 0 ? (
              <p className="dispatch-empty">No status history is available in this projection.</p>
            ) : (
              <ol className="dispatch-timeline">
                {selectedParcel.history.map((item, index) => (
                  <li key={`${item.changedAt}-${index}`}>
                    <strong>{item.newStatus.replaceAll('_', ' ')}</strong>
                    <time>{new Date(item.changedAt).toLocaleString()}</time>
                    {item.note && <p>{item.note}</p>}
                  </li>
                ))}
              </ol>
            )}
            {selectedParcel?.error && (
              <div className="dispatch-dialog-backdrop">
                <section className="dispatch-dialog card" role="dialog" aria-modal="true"
                  aria-labelledby="parcel-detail-error-title">
                  <button className="dispatch-dialog-close" aria-label="Close parcel details"
                    onClick={() => setSelectedParcel(null)}>×</button>
                  <h2 id="parcel-detail-error-title">Unable to load parcel details</h2>
                  <p className="dispatch-error" role="alert">{selectedParcel.error}</p>
                </section>
              </div>
            )}
            {selectedParcel && !selectedParcel.history && (
              <div className="dispatch-dialog-backdrop">
                <section className="dispatch-dialog card" role="dialog" aria-modal="true"
                  aria-label="Loading parcel details">
                  <LoadingSpinner label="Loading parcel details" />
                </section>
              </div>
            )}
          </section>
        </div>
      )}
    </section>
  )
}
