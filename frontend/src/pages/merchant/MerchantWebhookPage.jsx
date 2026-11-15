import { useEffect, useRef, useState } from 'react'
import Button from '../../components/Button'
import FormField from '../../components/FormField'
import LoadingSpinner from '../../components/LoadingSpinner'
import { merchantService } from '../../services/merchantService'
import { webhookDeliveries } from '../../services/webhookDeliveries'
import WebhookPageLayout from './webhooks/WebhookPageLayout'
import WebhookActionIcon from './webhooks/WebhookActionIcon'
import WebhookDialog from './webhooks/WebhookDialog'
import MerchantProfileSetup from './webhooks/MerchantProfileSetup'

const statuses = [
  ['Created', 'CREATED'], ['Picked Up', 'PICKED_UP'],
  ['In Transit', 'IN_TRANSIT'], ['Out for Delivery', 'OUT_FOR_DELIVERY'],
  ['Delivered', 'DELIVERED'], ['Failed', 'FAILED'], ['Cancelled', 'CANCELLED'],
]
const emptySettings = { url: '', enabled: true }

export default function MerchantWebhookPage() {
  const [merchant, setMerchant] = useState(null)
  const [settings, setSettings] = useState(emptySettings)
  const [savedSettings, setSavedSettings] = useState(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [profileMissing, setProfileMissing] = useState(false)
  const [loadVersion, setLoadVersion] = useState(0)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [operation, setOperation] = useState('')
  const [newSecret, setNewSecret] = useState(null)
  const [copied, setCopied] = useState(false)
  const [rotateOpen, setRotateOpen] = useState(false)
  const [rotateError, setRotateError] = useState('')
  const lock = useRef(false)

  useEffect(() => {
    const controller = new AbortController()
    async function load() {
      try {
        let profile
        try { profile = await merchantService.getMyMerchant(controller.signal) }
        catch (err) {
          if (err.status === 404) {
            if (!controller.signal.aborted) setProfileMissing(true)
            return
          }
          throw err
        }
        let config = null
        if (profile?.status === 'APPROVED') {
          try { config = await merchantService.getWebhookSettings(controller.signal) }
          catch (err) { if (err.status !== 404) throw err }
        }
        if (controller.signal.aborted) return
        setMerchant(profile)
        setProfileMissing(false)
        setSavedSettings(config)
        setSettings(config || emptySettings)
      } catch (err) {
        if (!controller.signal.aborted) setLoadError(err.message || 'Unable to load webhook settings.')
      } finally {
        if (!controller.signal.aborted) setLoading(false)
      }
    }
    load()
    return () => controller.abort()
  }, [loadVersion])

  const approved = merchant?.status === 'APPROVED'
  const busy = Boolean(operation)
  const dirty = !savedSettings || settings.url.trim() !== savedSettings.url || settings.enabled !== savedSettings.enabled
  const canTest = approved && savedSettings?.enabled && settings.enabled && !dirty

  async function save(event) {
    event.preventDefault()
    if (lock.current || !approved) return
    setError(''); setMessage('')
    let endpoint
    try { endpoint = new URL(settings.url.trim()) } catch { /* handled below */ }
    if (!endpoint || endpoint.protocol !== 'https:' || !endpoint.hostname || endpoint.username || endpoint.password || endpoint.hash) {
      setError('Webhook URL must be a valid HTTPS URL.')
      return
    }
    lock.current = true; setOperation('save')
    try {
      const response = await merchantService.updateWebhookSettings(settings.url.trim(), settings.enabled)
      const config = response.settings
      setSettings(config); setSavedSettings(config)
      if (response.signingSecret) { setNewSecret(response.signingSecret); setCopied(false) }
      setMessage(`Webhook settings saved. Delivery updates are ${config.enabled ? 'enabled' : 'paused'}.`)
    } catch (err) { setError(err.message || 'Failed to save webhook settings.') }
    finally { lock.current = false; setOperation('') }
  }

  async function sendTest() {
    if (lock.current || !canTest) return
    lock.current = true; setOperation('test'); setError(''); setMessage('')
    try {
      await webhookDeliveries.test()
      setMessage('Test event queued. Open Delivery History to check the receiver outcome.')
    } catch (err) { setError(err.message || 'Unable to queue the test event.') }
    finally { lock.current = false; setOperation('') }
  }

  async function rotate() {
    if (lock.current || !approved || !savedSettings) return
    lock.current = true; setOperation('rotate'); setRotateError('')
    try {
      const response = await merchantService.rotateWebhookSecret()
      setSavedSettings(response.settings)
      setNewSecret(response.signingSecret); setCopied(false)
      setRotateOpen(false)
      setMessage('Signing secret rotated. Update your receiver with the new secret.')
    } catch (err) { setRotateError(err.message || 'Failed to rotate signing secret.') }
    finally { lock.current = false; setOperation('') }
  }

  async function copySecret() {
    try {
      await navigator.clipboard.writeText(newSecret)
      setCopied(true)
    } catch { setError('Unable to copy the secret. Select and copy it manually.') }
  }

  return <WebhookPageLayout>
    {message && <div className="alert alert-success webhook-feedback" role="status">{message}</div>}
    {error && <div className="alert alert-error webhook-feedback" role="alert">{error}</div>}
    {loading ? <div className="webhook-status-card"><LoadingSpinner /><p role="status">Loading webhook settings…</p></div>
      : loadError ? <div className="webhook-status-card" role="alert">
        <h2>Unable to load webhook settings</h2><p>{loadError}</p>
        <Button onClick={() => { setLoadError(''); setLoading(true); setLoadVersion((n) => n + 1) }}>Try again</Button>
      </div> : profileMissing ? <MerchantProfileSetup
        onRefresh={() => { setLoadError(''); setLoading(true); setLoadVersion((n) => n + 1) }}
        onRegistered={(profile) => {
          setMerchant(profile); setProfileMissing(false)
          setMessage('Business profile submitted. An administrator must approve it before you can configure webhooks.')
        }} /> : <>
        {!approved && <div className="info-banner info-banner-warning webhook-feedback" role="alert">
          <div className="info-banner-content"><strong>Webhook settings require business approval</strong>
            Your business profile status is {merchant?.status || 'PENDING'}. Ask an administrator to review it, then refresh this page.
          </div>
        </div>}
        <section className="webhook-endpoint-card" aria-label="Endpoint configuration">
          <div className="webhook-endpoint-heading"><h2>Endpoint</h2></div>
          <form className="webhook-endpoint-form" onSubmit={save} noValidate>
            <FormField id="webhook-url" label="Endpoint URL" type="url" required
              hint="Use an HTTPS endpoint that accepts POST requests."
              value={settings.url} onChange={(event) => setSettings({ ...settings, url: event.target.value })}
              disabled={!approved || busy} placeholder="https://your-store.example/hooks/swiftdrop" />
            <section className="webhook-event-section" aria-labelledby="webhook-events-title">
              <h3 id="webhook-events-title">Status-change events</h3>
              <ul className="webhook-event-grid">
                {statuses.map(([label, status]) => <li key={status}>
                  <WebhookActionIcon name="check" /><span>{label}</span><code>{status}</code>
                </li>)}
              </ul>
              <p className="webhook-help">All parcel status changes are sent while the endpoint is enabled.</p>
            </section>
            <label className="webhook-enable-control">
              <input type="checkbox" role="switch" checked={settings.enabled} disabled={!approved || busy}
                onChange={(event) => setSettings({ ...settings, enabled: event.target.checked })}
                aria-label="Endpoint enabled" />
              <span className={`toggle-switch ${settings.enabled ? 'toggle-switch-on' : ''}`} aria-hidden="true"><span className="toggle-switch-handle" /></span>
              <span>Endpoint {settings.enabled ? 'enabled' : 'disabled'}</span>
            </label>
            <section className="secret-section" aria-labelledby="webhook-secret-title">
              <div className="secret-header">
                <h3 id="webhook-secret-title" className="secret-title">Signing secret</h3>
                <Button variant="secondary" size="sm" disabled={!approved || !savedSettings || busy}
                  onClick={() => { setRotateError(''); setRotateOpen(true) }}>Rotate secret</Button>
              </div>
              <p className="webhook-help">Verify requests using the <code>X-SwiftDrop-Signature</code> and timestamp headers.</p>
              {newSecret ? <>
                <p className="webhook-help">Copy this secret now. It is only shown after generation and is cleared when you leave this page.</p>
                <div className="secret-display-box secret-new"><code>{newSecret}</code>
                  <Button variant="secondary" size="sm" onClick={copySecret}>{copied ? 'Copied' : 'Copy'}</Button>
                </div>
              </> : savedSettings ? <div className="secret-display-box">
                <span aria-label="Signing secret hidden">••••••••••••••••••••••••</span>
                <span className="secret-rotated-time">Secret saved securely</span>
              </div> : <p className="webhook-help">A secret will be generated automatically when you save your configuration.</p>}
            </section>
            {approved && (!savedSettings || dirty) && <p className="webhook-help">Save changes before sending a test event.</p>}
            {approved && savedSettings && !savedSettings.enabled && !dirty && <p className="webhook-help">Enable the endpoint and save changes before sending a test event.</p>}
            <div className="webhook-form-actions">
              <Button variant="secondary" disabled={busy || !canTest} loading={operation === 'test'} loadingText="Queuing…" onClick={sendTest}>
                <WebhookActionIcon name="send" />Send Test Event
              </Button>
              <Button type="submit" disabled={busy || !approved} loading={operation === 'save'} loadingText="Saving…">Save changes</Button>
            </div>
          </form>
        </section>
        <p className="webhook-footnote">Failed deliveries retry after 1, 5, 15 and 60 minutes, up to five attempts in total.
          Payloads include the tracking number, your order reference, the new status, event ID and timestamp.</p>
      </>}
    {rotateOpen && <WebhookDialog title="Rotate signing secret" onClose={() => setRotateOpen(false)} busy={busy}
      footer={<><Button variant="secondary" disabled={busy} onClick={() => setRotateOpen(false)}>Cancel</Button>
        <Button loading={operation === 'rotate'} loadingText="Rotating…" onClick={rotate}>Rotate secret</Button></>}>
      {rotateError && <div className="alert alert-error" role="alert">{rotateError}</div>}
      <p>Invalidate the current signing secret and issue a new one?</p>
      <p className="webhook-secret-notice">Update your receiver immediately after rotating. Requests signed with the new secret cannot be verified using the old one.</p>
    </WebhookDialog>}
  </WebhookPageLayout>
}
