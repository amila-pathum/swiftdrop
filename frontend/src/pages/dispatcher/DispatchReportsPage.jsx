import { useCallback, useEffect, useState } from 'react'
import Button from '../../components/Button'
import LoadingSpinner from '../../components/LoadingSpinner'
import { dispatchService } from '../../services/dispatchService'
import { statusLabel } from './dispatchFormat'

const today = () => new Date().toISOString().slice(0, 10)
const weekAgo = () => new Date(Date.now() - 6 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10)

function BarChart({ items, label, value, title }) {
  if (!items.length) return <p className="dispatch-empty">No data for this period.</p>
  const maximum = Math.max(1, ...items.map((item) => Number(value(item)) || 0))

  return (
    <div className="dispatch-bars" role="img" aria-label={title}>
      {items.map((item) => {
        const amount = Number(value(item)) || 0
        return (
          <div className="dispatch-bar-row" key={label(item)}>
            <span title={label(item)}>{label(item)}</span>
            <div className="dispatch-bar-track">
              <div className="dispatch-bar" style={{ width: `${(amount / maximum) * 100}%` }} />
            </div>
            <strong>{amount}</strong>
          </div>
        )
      })}
    </div>
  )
}

/** One stacked bar per day: delivered then failed. */
function DailyChart({ days }) {
  if (!days.length) return <p className="dispatch-empty">No data for this period.</p>
  const maximum = Math.max(1, ...days.map((day) => day.total))

  return (
    <>
      <div className="dispatch-legend" aria-hidden="true">
        <span><i className="dispatch-swatch delivered" />Delivered</span>
        <span><i className="dispatch-swatch failed" />Failed</span>
      </div>
      <div className="dispatch-bars" role="img"
        aria-label={`Deliveries per day: ${days.map((day) =>
          `${day.date} ${day.delivered} delivered, ${day.failed} failed`).join('; ')}`}>
        {days.map((day) => (
          <div className="dispatch-bar-row" key={day.date} data-testid="daily-bar">
            <span>{day.date}</span>
            <div className="dispatch-bar-track stacked">
              <div className="dispatch-bar delivered" style={{ width: `${(day.delivered / maximum) * 100}%` }}
                title={`${day.delivered} delivered`} />
              <div className="dispatch-bar failed" style={{ width: `${(day.failed / maximum) * 100}%` }}
                title={`${day.failed} failed`} />
            </div>
            <strong>{day.total}</strong>
          </div>
        ))}
      </div>
    </>
  )
}

export default function DispatchReportsPage() {
  const [from, setFrom] = useState(weekAgo)
  const [to, setTo] = useState(today)
  const [period, setPeriod] = useState(() => ({ from: weekAgo(), to: today() }))
  const [summary, setSummary] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const loadSummary = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setSummary(await dispatchService.getSummary(period))
    } catch (requestError) {
      setError(requestError.message || 'Unable to load dispatch reports.')
    } finally {
      setLoading(false)
    }
  }, [period])

  useEffect(() => {
    const timer = window.setTimeout(loadSummary, 0)
    return () => window.clearTimeout(timer)
  }, [loadSummary])

  const submitRange = (event) => {
    event.preventDefault()
    if (from && to && from > to) {
      setError('Start date must not be after end date.')
      return
    }
    setPeriod({ from, to })
  }

  const days = summary?.deliveriesPerDay || []
  const delivered = days.reduce((sum, day) => sum + day.delivered, 0)
  const failed = days.reduce((sum, day) => sum + day.failed, 0)

  return (
    <section className="dispatch-page">
      <header className="dispatch-page-header">
        <div>
          <h1>Dispatch reports</h1>
          <p>Calculated from the parcel status events stored by Dispatch. Dates are in UTC.</p>
        </div>
      </header>

      <form className="dispatch-range card" onSubmit={submitRange}>
        <label>From<input type="date" value={from} onChange={(event) => setFrom(event.target.value)} required /></label>
        <label>To<input type="date" value={to} onChange={(event) => setTo(event.target.value)} required /></label>
        <Button type="submit" loading={loading} loadingText="Loading reports…">Apply dates</Button>
      </form>

      {error && <p className="dispatch-error" role="alert">{error}</p>}
      {loading && !summary ? (
        <div className="dispatch-loading"><LoadingSpinner label="Loading reports" /></div>
      ) : summary ? (
        <>
          <div className="dispatch-summary-grid">
            <article className="dispatch-summary card"><span>Delivered</span><strong>{delivered}</strong></article>
            <article className="dispatch-summary card"><span>Failed</span><strong>{failed}</strong></article>
            <article className="dispatch-summary card">
              <span>Success rate</span><strong>{Number(summary.successRate).toFixed(2)}%</strong>
            </article>
            <article className="dispatch-summary card">
              <span>Failure rate</span><strong>{Number(summary.failureRate).toFixed(2)}%</strong>
            </article>
          </div>

          <div className="dispatch-report-grid">
            <section className="dispatch-report-card card">
              <h2>Deliveries per day</h2>
              <DailyChart days={days} />
            </section>
            <section className="dispatch-report-card card">
              <h2>Active deliveries per courier</h2>
              <BarChart items={summary.activeDeliveriesPerCourier}
                label={(item) => item.courierName || `Courier user ${item.courierUserId}`}
                value={(item) => item.count}
                title="Parcels out with each courier at the end of the period" />
            </section>
            <section className="dispatch-report-card card">
              <h2>Status events</h2>
              <BarChart items={summary.countsByStatus} label={(item) => statusLabel(item.status)}
                value={(item) => item.count} title="Parcel status events in the period, by status" />
            </section>
          </div>
        </>
      ) : (
        <div className="dispatch-empty card">No reports could be loaded.</div>
      )}
    </section>
  )
}
