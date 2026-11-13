import { useCallback, useEffect, useMemo, useState } from 'react'
import Button from '../../components/Button'
import LoadingSpinner from '../../components/LoadingSpinner'
import { dispatchService } from '../../services/dispatchService'

const today = () => new Date().toISOString().slice(0, 10)
const weekAgo = () => new Date(Date.now() - 6 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10)

function BarChart({ items, labelField, valueField, title }) {
  const maximum = Math.max(1, ...items.map((item) => Number(item[valueField]) || 0))
  if (!items.length) return <p className="dispatch-empty">No data for this period.</p>

  return (
    <div className="dispatch-bars" role="img" aria-label={title}>
      {items.map((item) => {
        const value = Number(item[valueField]) || 0
        return (
          <div className="dispatch-bar-row" key={item[labelField]}>
            <span title={item[labelField]}>{item[labelField]}</span>
            <div className="dispatch-bar-track">
              <div className="dispatch-bar" style={{ width: `${(value / maximum) * 100}%` }} />
            </div>
            <strong>{value}</strong>
          </div>
        )
      })}
    </div>
  )
}

function ReportTable({ title, children, empty, hasRows }) {
  return (
    <section className="dispatch-report-card card">
      <h2>{title}</h2>
      {hasRows ? <div className="dispatch-table-wrap"><table className="dispatch-table">{children}</table></div>
        : <p className="dispatch-empty">{empty}</p>}
    </section>
  )
}

export default function DispatchReportsPage() {
  const [from, setFrom] = useState(weekAgo)
  const [to, setTo] = useState(today)
  const [groupBy, setGroupBy] = useState('day')
  const [period, setPeriod] = useState(() => ({ from: weekAgo(), to: today(), groupBy: 'day' }))
  const [reports, setReports] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [exporting, setExporting] = useState('')

  const loadReports = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [status, volume, courierPerformance, failedDeliveries, revenue] = await Promise.all([
        dispatchService.getReport('status', period),
        dispatchService.getReport('volume', period),
        dispatchService.getReport('courier-performance', period),
        dispatchService.getReport('failed-deliveries', period),
        dispatchService.getReport('revenue-by-route', period),
      ])
      setReports({ status, volume, courierPerformance, failedDeliveries, revenue })
    } catch (requestError) {
      setError(requestError.message || 'Unable to load reports.')
    } finally {
      setLoading(false)
    }
  }, [period])

  useEffect(() => {
    const initialRequest = window.setTimeout(loadReports, 0)
    return () => window.clearTimeout(initialRequest)
  }, [loadReports])

  const exportCsv = async (name) => {
    setExporting(name)
    setError('')
    try {
      const csv = await dispatchService.exportReport(name, period)
      const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
      const link = document.createElement('a')
      link.href = url
      link.download = `${name}-${from}-to-${to}.csv`
      link.click()
      URL.revokeObjectURL(url)
    } catch (requestError) {
      setError(requestError.message || 'CSV export failed.')
    } finally {
      setExporting('')
    }
  }

  const totals = useMemo(() => {
    const statuses = reports?.status || []
    const count = (name) => statuses.find((item) => item.status === name)?.count || 0
    return {
      delivered: count('DELIVERED'),
      failed: count('FAILED'),
      created: count('CREATED'),
      fees: (reports?.revenue || []).reduce((sum, item) => sum + Number(item.totalFees || 0), 0),
    }
  }, [reports])

  const submitRange = (event) => {
    event.preventDefault()
    if (from && to && from > to) {
      setError('Start date must not be after end date.')
      return
    }
    setPeriod({ from, to, groupBy })
  }

  return (
    <section className="dispatch-page">
      <header className="dispatch-page-header">
        <div>
          <h1>Dispatch reports</h1>
          <p>Delivery activity and courier performance for the selected period.</p>
        </div>
      </header>

      <form className="dispatch-range card" onSubmit={submitRange}>
        <label>From<input type="date" value={from} onChange={(event) => setFrom(event.target.value)} required /></label>
        <label>To<input type="date" value={to} onChange={(event) => setTo(event.target.value)} required /></label>
        <label>Volume grouping<select value={groupBy} onChange={(event) => setGroupBy(event.target.value)}>
          <option value="day">Daily</option><option value="week">Weekly</option>
        </select></label>
        <Button type="submit" loading={loading} loadingText="Loading reports…">Apply dates</Button>
      </form>

      {error && <p className="dispatch-error" role="alert">{error}</p>}

      {loading && !reports ? (
        <div className="dispatch-loading"><LoadingSpinner label="Loading reports" /></div>
      ) : reports ? (
        <>
          <div className="dispatch-summary-grid">
            <article className="dispatch-summary card"><span>Created</span><strong>{totals.created}</strong></article>
            <article className="dispatch-summary card"><span>Delivered</span><strong>{totals.delivered}</strong></article>
            <article className="dispatch-summary card"><span>Failed</span><strong>{totals.failed}</strong></article>
            <article className="dispatch-summary card"><span>Recorded fees</span><strong>{totals.fees.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</strong></article>
          </div>

          <div className="dispatch-report-grid">
            <section className="dispatch-report-card card">
              <div className="dispatch-section-heading">
                <h2>Deliveries by status</h2>
                <Button variant="secondary" loading={exporting === 'status'} onClick={() => exportCsv('status')}>
                  Export CSV
                </Button>
              </div>
              <BarChart items={reports.status} labelField="status" valueField="count" title="Parcel status counts" />
            </section>

            <section className="dispatch-report-card card">
              <div className="dispatch-section-heading">
                <h2>Volume per {period.groupBy}</h2>
                <Button variant="secondary" loading={exporting === 'volume'} onClick={() => exportCsv('volume')}>
                  Export CSV
                </Button>
              </div>
              <BarChart items={reports.volume} labelField="period" valueField="count"
                title={`Delivered parcels by ${period.groupBy}`} />
            </section>
          </div>

          <ReportTable title="Courier performance" hasRows={reports.courierPerformance.length > 0}
            empty="No courier activity for this period.">
            <thead><tr><th>Courier</th><th>Handled</th><th>Delivered</th><th>Failed</th><th>Average delivery time</th></tr></thead>
            <tbody>{reports.courierPerformance.map((courier) => (
              <tr key={courier.courierId}>
                <td>{courier.courierName}</td><td>{courier.parcelsHandled}</td>
                <td>{courier.delivered}</td><td>{courier.failed}</td>
                <td>{courier.averageDeliveryHours == null ? '—' : `${courier.averageDeliveryHours} hours`}</td>
              </tr>
            ))}</tbody>
          </ReportTable>
          <div className="dispatch-table-action"><Button variant="secondary"
            loading={exporting === 'courier-performance'} onClick={() => exportCsv('courier-performance')}>
            Export courier CSV
          </Button></div>

          <ReportTable title="Failed deliveries" hasRows={reports.failedDeliveries.length > 0}
            empty="No failed deliveries for this period.">
            <thead><tr><th>Tracking number</th><th>Courier</th><th>Route</th><th>Reason</th><th>Failed at</th></tr></thead>
            <tbody>{reports.failedDeliveries.map((parcel) => (
              <tr key={`${parcel.trackingNumber}-${parcel.failedAt}`}>
                <td>{parcel.trackingNumber}</td><td>{parcel.courierName || '—'}</td>
                <td>{parcel.pickupArea} → {parcel.dropoffArea}</td>
                <td>{parcel.reason || 'Reason not supplied'}</td>
                <td>{new Date(parcel.failedAt).toLocaleString()}</td>
              </tr>
            ))}</tbody>
          </ReportTable>
          <div className="dispatch-table-action"><Button variant="secondary"
            loading={exporting === 'failed-deliveries'} onClick={() => exportCsv('failed-deliveries')}>
            Export failed deliveries CSV
          </Button></div>

          <ReportTable title="Fees by route" hasRows={reports.revenue.length > 0}
            empty="No fee data for this period.">
            <thead><tr><th>Pickup area</th><th>Drop-off area</th><th>Parcels</th><th>Recorded fees</th></tr></thead>
            <tbody>{reports.revenue.map((route) => (
              <tr key={`${route.pickupArea}-${route.dropoffArea}`}>
                <td>{route.pickupArea}</td><td>{route.dropoffArea}</td><td>{route.parcelCount}</td>
                <td>{Number(route.totalFees).toLocaleString(undefined, { minimumFractionDigits: 2 })}</td>
              </tr>
            ))}</tbody>
          </ReportTable>
          <div className="dispatch-table-action"><Button variant="secondary"
            loading={exporting === 'revenue-by-route'} onClick={() => exportCsv('revenue-by-route')}>
            Export route fees CSV
          </Button></div>
        </>
      ) : (
        <div className="dispatch-empty card">No reports could be loaded.</div>
      )}
    </section>
  )
}
