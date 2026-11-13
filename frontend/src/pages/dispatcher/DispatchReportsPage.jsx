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

  const counts = useMemo(() => {
    const statuses = summary?.countsByStatus || []
    const count = (status) => statuses.find((item) => item.status === status)?.count || 0
    const total = count('DELIVERED') + count('FAILED')
    return {
      delivered: count('DELIVERED'),
      failed: count('FAILED'),
      successRate: Number(summary?.successRate) || 0,
      failureRate: total ? 100 - (Number(summary?.successRate) || 0) : 0,
    }
  }, [summary])

  const submitRange = (event) => {
    event.preventDefault()
    if (from && to && from > to) {
      setError('Start date must not be after end date.')
      return
    }
    setPeriod({ from, to })
  }

  return (
    <section className="dispatch-page">
      <header className="dispatch-page-header">
        <div>
          <h1>Dispatch reports</h1>
          <p>All report totals are calculated from the status events stored by Dispatch.</p>
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
            <article className="dispatch-summary card"><span>Delivered</span><strong>{counts.delivered}</strong></article>
            <article className="dispatch-summary card"><span>Failed</span><strong>{counts.failed}</strong></article>
            <article className="dispatch-summary card"><span>Success rate</span><strong>{counts.successRate.toFixed(2)}%</strong></article>
            <article className="dispatch-summary card"><span>Failure rate</span><strong>{counts.failureRate.toFixed(2)}%</strong></article>
          </div>

          <div className="dispatch-report-grid">
            <section className="dispatch-report-card card">
              <h2>Deliveries per day</h2>
              <BarChart items={summary.deliveriesPerDay} labelField="date" valueField="total"
                title="Delivered and failed deliveries per day" />
            </section>
            <section className="dispatch-report-card card">
              <h2>Counts by status</h2>
              <BarChart items={summary.countsByStatus} labelField="status" valueField="count"
                title="Stored parcel status event counts" />
            </section>
            <section className="dispatch-report-card card">
              <h2>Active deliveries per courier</h2>
              <BarChart items={summary.activeDeliveriesPerCourier} labelField="courierId" valueField="count"
                title="Active deliveries grouped by courier" />
            </section>
          </div>
        </>
      ) : (
        <div className="dispatch-empty card">No reports could be loaded.</div>
      )}
    </section>
  )
}
