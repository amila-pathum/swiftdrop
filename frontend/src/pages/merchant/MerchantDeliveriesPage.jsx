import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import Button from '../../components/Button'
import LoadingSpinner from '../../components/LoadingSpinner'
import { webhookDeliveries } from '../../services/webhookDeliveries'
import WebhookPageLayout from './webhooks/WebhookPageLayout'
import WebhookActionIcon from './webhooks/WebhookActionIcon'
import WebhookDialog from './webhooks/WebhookDialog'

function formatTime(value) {
  if (!value || Number.isNaN(new Date(value).getTime())) return '—'
  return new Intl.DateTimeFormat('en-GB', {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}

function Timestamp({ value }) {
  return value ? <time dateTime={value}>{formatTime(value)}</time> : '—'
}

function Outcome({ state }) {
  const labels = { DELIVERED: 'Delivered', RETRY_SCHEDULED: 'Retry Scheduled', FAILED: 'Failed', PENDING: 'Queued', SKIPPED: 'Skipped' }
  return <span className={`webhook-outcome webhook-outcome-${state?.toLowerCase()}`}>{labels[state] || state || 'Unknown'}</span>
}

export default function MerchantDeliveriesPage() {
  const [page, setPage] = useState(0)
  const [failed, setFailed] = useState(false)
  const [version, setVersion] = useState(0)
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [actionError, setActionError] = useState('')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const [detail, setDetail] = useState(null)
  const lock = useRef(false)
  const detailRequest = useRef(null)

  useEffect(() => {
    const controller = new AbortController()
    webhookDeliveries.list(page, failed, controller.signal)
      .then((response) => {
        if (controller.signal.aborted) return
        const lastPage = Math.max(0, Math.ceil(response.total / 20) - 1)
        if (page > lastPage) {
          setPage(lastPage)
          return
        }
        setResult(response); setLoading(false)
      })
      .catch((err) => {
        if (!controller.signal.aborted) { setLoadError(err.message || 'Unable to load deliveries.'); setLoading(false) }
      })
    return () => controller.abort()
  }, [page, failed, version])
  useEffect(() => () => detailRequest.current?.abort(), [])

  function refresh() {
    setLoadError(''); setActionError(''); setDetail(null); setLoading(true); setVersion((n) => n + 1)
  }
  function changePage(next) {
    setPage(next); setLoading(true); setLoadError(''); setActionError(''); setDetail(null)
  }
  async function retry(id) {
    if (lock.current) return
    lock.current = true; setBusy(true); setActionError(''); setMessage('')
    try {
      await webhookDeliveries.retry(id)
      setMessage('Retry queued. Previous attempts are retained.'); refresh()
    } catch (err) { setActionError(err.message || 'Unable to retry this delivery.') }
    finally { lock.current = false; setBusy(false) }
  }
  async function showDetails(id) {
    if (lock.current) return
    const controller = new AbortController()
    detailRequest.current = controller
    lock.current = true; setBusy(true); setActionError('')
    try {
      const response = await webhookDeliveries.detail(id, controller.signal)
      if (!controller.signal.aborted) setDetail(response)
    } catch (err) {
      if (!controller.signal.aborted) setActionError(err.message || 'Unable to load attempt history.')
    } finally {
      lock.current = false; setBusy(false)
    }
  }
  const total = result?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / 20))
  const firstItem = result?.items.length ? page * 20 + 1 : 0
  const lastItem = result?.items.length ? page * 20 + result.items.length : 0

  return <WebhookPageLayout total={!failed && !loading && !loadError ? total : undefined}>
    {message && <div className="alert alert-success webhook-feedback" role="status">{message}</div>}
    {actionError && <div className="alert alert-error webhook-feedback" role="alert">{actionError}</div>}
    <div className="webhook-history-toolbar">
      <label className="webhook-filter"><input type="checkbox" checked={failed} disabled={busy} onChange={(event) => {
        setFailed(event.target.checked); setPage(0); setLoading(true); setLoadError(''); setActionError(''); setDetail(null)
      }} />Failed only</label>
      <Button variant="secondary" size="sm" disabled={busy || loading} onClick={refresh}>
        <WebhookActionIcon name="retry" />Refresh
      </Button>
    </div>
    {loading ? <div className="webhook-status-card"><LoadingSpinner /><p role="status">Loading deliveries…</p></div>
      : loadError ? <div className="webhook-status-card" role="alert">
        <h2>Unable to load delivery history</h2><p>{loadError}</p>
        {loadError === 'Merchant profile not found' && <p><Link to="/merchant/webhooks">Set up your business profile</Link> to enable webhook delivery history.</p>}
        <Button onClick={refresh}>Try again</Button>
      </div> : <>
        <div className="webhook-history-card">
          {!result?.items.length ? <div className="webhook-status-card">
            <h2>No webhook deliveries found.</h2>
            <p>{failed ? 'There are no failed deliveries to retry.' : 'Send a test event from Endpoint settings to see your first delivery here.'}</p>
          </div> : <div className="table-wrapper">
            <table className="data-table webhook-history-table">
              <caption className="visually-hidden">Webhook delivery history</caption>
              <thead><tr>{['Status sent', 'Tracking no.', 'Created', 'Result', 'Attempts', 'Actions'].map((label) => <th key={label} scope="col">{label}</th>)}</tr></thead>
              <tbody>{result.items.map((row) => <tr key={row.id}>
                <td><code className="webhook-event-code">{row.parcelStatus}</code></td>
                <td>{row.trackingNumber}</td>
                <td><Timestamp value={row.createdAt} /></td>
                <td><Outcome state={row.state} />
                  {row.state === 'RETRY_SCHEDULED' && row.nextRetryAt && <p className="webhook-help">Next: <Timestamp value={row.nextRetryAt} /></p>}
                </td>
                <td><Button variant="ghost" size="sm" disabled={busy} aria-label={`View attempts for ${row.trackingNumber}`} onClick={() => showDetails(row.id)}>{row.attemptCount}</Button></td>
                <td><div className="webhook-row-actions">
                  {row.state === 'FAILED' && <Button variant="secondary" size="sm" disabled={busy} onClick={() => retry(row.id)}><WebhookActionIcon name="retry" />Retry</Button>}
                </div></td>
              </tr>)}</tbody>
            </table>
          </div>}
        </div>
        <div className="webhook-pagination">
          <span>Showing {firstItem}–{lastItem} of {total}</span>
          <div className="webhook-page-controls">
            <Button variant="secondary" size="sm" aria-label="Previous page" disabled={page === 0 || busy} onClick={() => changePage(page - 1)}><WebhookActionIcon name="previous" /></Button>
            <span>Page {page + 1} of {totalPages}</span>
            <Button variant="secondary" size="sm" aria-label="Next page" disabled={page + 1 >= totalPages || busy} onClick={() => changePage(page + 1)}><WebhookActionIcon name="next" /></Button>
          </div>
        </div>
        <p className="webhook-footnote">Select an attempt count to inspect its history. Only failed deliveries can be retried manually.</p>
      </>}
    {detail && <WebhookDialog title="Attempt history" onClose={() => setDetail(null)}>
      <dl className="webhook-detail-meta">
        <dt>Event ID</dt><dd>{detail.delivery.eventId}</dd>
        <dt>Tracking number</dt><dd>{detail.delivery.trackingNumber}</dd>
        <dt>Order reference</dt><dd>{detail.delivery.merchantOrderReference || '—'}</dd>
        <dt>Status sent</dt><dd>{detail.delivery.parcelStatus}</dd>
        <dt>Delivery result</dt><dd><Outcome state={detail.delivery.state} /></dd>
      </dl>
      {!detail.attempts.length ? <p>No attempts yet.</p> : <ol className="webhook-attempt-list">
        {detail.attempts.map((attempt) => <li key={attempt.attemptNumber}>
          <div className="webhook-attempt-heading"><strong>Attempt {attempt.attemptNumber}</strong><Outcome state={attempt.outcome} /></div>
          <p><Timestamp value={attempt.attemptedAt} /> · HTTP {attempt.httpStatus ?? '—'}</p>
          {attempt.failureCode && <p>Failure: {attempt.failureCode}</p>}
          {attempt.nextRetryAt && <p>Next retry: <Timestamp value={attempt.nextRetryAt} /></p>}
        </li>)}
      </ol>}
    </WebhookDialog>}
  </WebhookPageLayout>
}
