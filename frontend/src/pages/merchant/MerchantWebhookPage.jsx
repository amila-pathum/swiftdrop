import { useEffect, useState } from 'react'
import Button from '../../components/Button'
import FormField from '../../components/FormField'
import Input from '../../components/Input'
import LoadingSpinner from '../../components/LoadingSpinner'
import { merchantService } from '../../services/merchantService'

function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  return !Number.isNaN(date.getTime())
    ? date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
    : '—'
}

export default function MerchantWebhookPage() {
  const [merchant, setMerchant] = useState(null)
  const [url, setUrl] = useState('')
  const [enabled, setEnabled] = useState(true)
  const [lastRotated, setLastRotated] = useState(null)
  const [isConfigured, setIsConfigured] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  // Status feedback
  const [successMessage, setSuccessMessage] = useState('')
  const [formError, setFormError] = useState(null)
  const [saving, setSaving] = useState(false)

  // New signing secret (shown only once)
  const [newSecret, setNewSecret] = useState(null)
  const [copiedSecret, setCopiedSecret] = useState(false)

  // Rotate Modal
  const [showRotateModal, setShowRotateModal] = useState(false)
  const [rotating, setRotating] = useState(false)
  const [rotateError, setRotateError] = useState(null)

  const handleRetry = () => {
    setLoading(true)
    setError(null)
    merchantService.getMyMerchant()
      .then((profile) => {
        setMerchant(profile)
        if (profile?.status === 'APPROVED') {
          return merchantService.getWebhookSettings()
            .then((config) => {
              if (config) {
                setUrl(config.url || '')
                setEnabled(config.enabled ?? true)
                setLastRotated(config.updatedAt || null)
                setIsConfigured(true)
              }
              setLoading(false)
            })
            .catch((webhookErr) => {
              if (webhookErr?.status === 404) {
                setIsConfigured(false)
                setUrl('')
                setEnabled(true)
              } else {
                setError(webhookErr)
              }
              setLoading(false)
            })
        }
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
      .then((profile) => {
        if (!ignore) {
          setMerchant(profile)
          if (profile?.status === 'APPROVED') {
            return merchantService.getWebhookSettings(controller.signal)
              .then((config) => {
                if (!ignore && config) {
                  setUrl(config.url || '')
                  setEnabled(config.enabled ?? true)
                  setLastRotated(config.updatedAt || null)
                  setIsConfigured(true)
                }
                if (!ignore) {
                  setLoading(false)
                }
              })
              .catch((webhookErr) => {
                if (!ignore) {
                  if (webhookErr?.status === 404) {
                    setIsConfigured(false)
                    setUrl('')
                    setEnabled(true)
                  } else if (webhookErr?.name !== 'AbortError') {
                    setError(webhookErr)
                  }
                  setLoading(false)
                }
              })
          }
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

  const handleSaveSettings = async (e) => {
    e.preventDefault()
    setFormError(null)
    setSuccessMessage('')

    if (!url.trim()) {
      setFormError('Webhook endpoint URL is required.')
      return
    }

    if (!url.trim().toLowerCase().startsWith('https://')) {
      setFormError('Webhook URL must be a valid HTTPS URL.')
      return
    }

    setSaving(true)
    try {
      const response = await merchantService.updateWebhookSettings(url.trim(), enabled)
      setIsConfigured(true)
      if (response?.settings) {
        setUrl(response.settings.url)
        setEnabled(response.settings.enabled)
        setLastRotated(response.settings.updatedAt)
      }
      if (response?.signingSecret) {
        setNewSecret(response.signingSecret)
        setCopiedSecret(false)
      }
      setSuccessMessage(`Webhook settings saved. Delivery updates are ${enabled ? 'enabled' : 'paused'}.`)
    } catch (err) {
      setFormError(err.message || 'Failed to save webhook settings.')
    } finally {
      setSaving(false)
    }
  }

  const handleRotateConfirm = async () => {
    setRotating(true)
    setRotateError(null)
    try {
      const response = await merchantService.rotateWebhookSecret()
      if (response?.settings) {
        setLastRotated(response.settings.updatedAt)
      }
      if (response?.signingSecret) {
        setNewSecret(response.signingSecret)
        setCopiedSecret(false)
      }
      setShowRotateModal(false)
      setSuccessMessage('Signing secret rotated successfully. Remember to update your application with the new secret.')
    } catch (err) {
      setRotateError(err.message || 'Failed to rotate signing secret.')
    } finally {
      setRotating(false)
    }
  }

  const handleCopySecret = () => {
    if (newSecret) {
      navigator.clipboard.writeText(newSecret)
      setCopiedSecret(true)
      setTimeout(() => setCopiedSecret(false), 2500)
    }
  }

  const isApproved = merchant?.status === 'APPROVED'

  return (
    <div className="merchant-scope merchant-webhooks">
      <nav className="page-breadcrumb" aria-label="Breadcrumb">
        <span>Workspace</span>
        <span>/</span>
        <strong>Webhook settings</strong>
      </nav>

      <header className="page-header-row">
        <div>
          <h1 className="page-title">Webhook Settings</h1>
          <p className="page-subtitle">Receive real-time updates about your parcel deliveries.</p>
        </div>
      </header>

      {/* Success Banner */}
      {successMessage && (
        <div className="alert alert-success" role="status" style={{ marginBottom: '20px' }}>
          <span>✓ {successMessage}</span>
          <button
            type="button"
            className="alert-dismiss"
            onClick={() => setSuccessMessage('')}
            aria-label="Dismiss notification"
          >
            Dismiss
          </button>
        </div>
      )}

      {loading ? (
        <div className="card status-card status-card-loading" style={{ margin: '30px auto' }}>
          <LoadingSpinner />
          <p role="status">Loading webhook settings…</p>
        </div>
      ) : error ? (
        <div className="card status-card" role="alert" style={{ margin: '30px auto' }}>
          <h2 className="card-title">Unable to load webhook settings</h2>
          <p className="card-subtitle">{error.message || 'An error occurred while fetching webhook configuration.'}</p>
          <Button onClick={handleRetry}>Try again</Button>
        </div>
      ) : (
        <>
          {/* Unapproved Banner */}
          {!isApproved && (
            <div className="info-banner info-banner-warning" role="alert">
              <span className="info-banner-icon" aria-hidden="true">⚠️</span>
              <div className="info-banner-content">
                <strong>Webhook settings require business approval</strong>
                Your merchant account status is <strong>{merchant?.status || 'PENDING'}</strong>. Webhook endpoints and live parcel delivery events will be enabled once your account is approved.
              </div>
            </div>
          )}

          <div className="webhook-grid">
            {/* Left Column: Endpoint configuration */}
            <section className="webhook-main-card" aria-label="Endpoint configuration">
              <div className="webhook-card-header">
                <h2>Endpoint configuration</h2>
                <p>Send delivery events directly to your application.</p>
              </div>

              <form onSubmit={handleSaveSettings} style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                {formError && (
                  <div className="alert alert-error" role="alert">
                    {formError}
                  </div>
                )}

                <FormField
                  id="webhook-url"
                  label="Webhook Endpoint URL"
                  hint="Must be a secure HTTPS URL capable of receiving POST events."
                  type="url"
                  value={url}
                  onChange={(e) => setUrl(e.target.value)}
                  placeholder="https://api.urbannest.co/webhooks/swiftdrop"
                  disabled={!isApproved}
                  required
                />

                {/* Toggle Row */}
                <div className="toggle-row">
                  <div className="toggle-info">
                    <strong>Enable Webhook</strong>
                    <p>Receive events at your endpoint. Turn off to pause delivery.</p>
                  </div>

                  <label className="toggle-control">
                    <input
                      type="checkbox"
                      checked={enabled}
                      onChange={(e) => isApproved && setEnabled(e.target.checked)}
                      disabled={!isApproved}
                      style={{ position: 'absolute', opacity: 0, width: 0, height: 0 }}
                      aria-label="Enable Webhook"
                    />
                    <span className={`toggle-switch ${enabled ? 'toggle-switch-on' : ''}`}>
                      <span className="toggle-switch-handle" />
                    </span>
                    <span className="toggle-status-label">{enabled ? 'Enabled' : 'Disabled'}</span>
                  </label>
                </div>

                {/* Signing Secret Section */}
                <div className="secret-section">
                  <div className="secret-header">
                    <span className="secret-title">Signing Secret</span>
                    <Button
                      type="button"
                      variant="secondary"
                      disabled={!isApproved || !isConfigured}
                      onClick={() => setShowRotateModal(true)}
                      title={!isConfigured ? 'Save endpoint first to generate a secret' : undefined}
                    >
                      Rotate Secret
                    </Button>
                  </div>

                  <p style={{ fontSize: '13px', color: 'var(--color-text-secondary)', margin: 0 }}>
                    Verify incoming requests using the <code>SwiftDrop-Signature</code> header.
                  </p>

                  {/* Secret display box */}
                  {newSecret ? (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                      <div className="modal-notice-box" style={{ margin: 0 }}>
                        <strong>New signing secret generated:</strong>
                        Copy it now. After navigating away, the secret will be masked.
                      </div>
                      <div className="secret-display-box" style={{ backgroundColor: '#f0fdf4', borderColor: '#bbf7d0' }}>
                        <code style={{ wordBreak: 'break-all' }}>{newSecret}</code>
                        <Button type="button" variant="secondary" onClick={handleCopySecret}>
                          {copiedSecret ? '✓ Copied' : 'Copy'}
                        </Button>
                      </div>
                    </div>
                  ) : isConfigured ? (
                    <div className="secret-display-box">
                      <span>whsec_••••••••••••••••••••••••••••</span>
                      <span className="secret-rotated-time">
                        {lastRotated ? `Last rotated ${formatDate(lastRotated)}` : 'Active'}
                      </span>
                    </div>
                  ) : (
                    <div className="secret-display-box" style={{ color: 'var(--color-text-muted)', fontStyle: 'italic' }}>
                      A secret will be generated automatically when you save your configuration.
                    </div>
                  )}

                  <p style={{ fontSize: '12px', color: 'var(--color-text-muted)', margin: 0 }}>
                    Secrets are only visible once, immediately after generation.
                  </p>
                </div>

                {/* Footer */}
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: '12px', paddingTop: '16px', borderTop: '1px solid var(--color-border)', flexWrap: 'wrap', gap: '12px' }}>
                  <span style={{ fontSize: '12px', color: 'var(--color-text-muted)' }}>
                    Changes apply to future delivery events.
                  </span>
                  <Button type="submit" disabled={saving || !isApproved}>
                    {saving ? 'Saving…' : 'Save Settings'}
                  </Button>
                </div>
              </form>
            </section>

            {/* Right Column: Information Cards */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
              <section className="info-card" aria-label="Delivery events">
                <h3>Delivery events</h3>
                <p>Your endpoint receives these events:</p>
                <div style={{ marginTop: '8px' }}>
                  <div className="event-item">
                    <span className="event-name">parcel.created</span>
                    <span className="event-desc">A new parcel is registered</span>
                  </div>
                  <div className="event-item">
                    <span className="event-name">parcel.in_transit</span>
                    <span className="event-desc">A parcel is on its way</span>
                  </div>
                  <div className="event-item">
                    <span className="event-name">parcel.delivered</span>
                    <span className="event-desc">Delivery is complete</span>
                  </div>
                  <div className="event-item">
                    <span className="event-name">parcel.delivery_failed</span>
                    <span className="event-desc">A delivery needs attention</span>
                  </div>
                </div>
              </section>

              <section className="info-card" aria-label="Set up a reliable endpoint">
                <h3>Set up a reliable endpoint</h3>
                <p>
                  Use HTTPS and return a 2xx response to acknowledge each event. Validate signatures before processing any delivery updates.
                </p>
                <div className="info-card-links">
                  <a href="#webhook-docs" className="action-link action-link-primary">
                    View webhook documentation ↗
                  </a>
                </div>
              </section>
            </div>
          </div>
        </>
      )}

      {/* Rotate Secret Confirmation Modal */}
      {showRotateModal && (
        <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="rotate-modal-title">
          <div className="modal-dialog">
            <div className="modal-header">
              <div>
                <h2 id="rotate-modal-title" className="modal-title">Rotate Signing Secret</h2>
                <p className="modal-subtitle">Invalidate the current secret and issue a new one</p>
              </div>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setShowRotateModal(false)}
                aria-label="Close dialog"
              >
                ✕
              </button>
            </div>

            <div className="modal-body">
              {rotateError && (
                <div className="alert alert-error" role="alert">
                  {rotateError}
                </div>
              )}
              <p style={{ margin: 0 }}>
                Are you sure you want to rotate your signing secret?
              </p>
              <div className="modal-notice-box" style={{ backgroundColor: '#fffbeb', borderColor: '#fde68a', color: '#92400e' }}>
                Your current secret will be immediately revoked. Any incoming webhook deliveries validated with the old secret will fail until you update your application configuration.
              </div>
            </div>

            <div className="modal-footer">
              <Button variant="secondary" type="button" onClick={() => setShowRotateModal(false)}>
                Cancel
              </Button>
              <Button type="button" onClick={handleRotateConfirm} disabled={rotating}>
                {rotating ? 'Rotating…' : 'Rotate Secret'}
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}