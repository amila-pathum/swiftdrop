import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
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

const EXAMPLE_ORDER_CURL = `curl -X POST https://api.swiftdrop.com/api/merchant/orders \\
  -H "Content-Type: application/json" \\
  -H "X-API-Key: sd_live_YOUR_KEY_HERE" \\
  -d '{
    "recipientName": "Olivia Bennett",
    "recipientPhone": "+1-555-0192",
    "deliveryAddress": "456 Oak Avenue, Austin, TX 78701",
    "weightKg": 1.5,
    "instructions": "Leave at front door"
  }'`

function ShieldCheckIcon() {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
      <polyline points="9 12 11 14 15 10" />
    </svg>
  )
}

function ReadyCheckIcon() {
  return (
    <span style={{
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      width: '32px',
      height: '32px',
      borderRadius: '50%',
      backgroundColor: '#f0fdf4',
      color: '#166534',
      border: '1px solid #bbf7d0',
      fontSize: '16px',
    }}>
      ✓
    </span>
  )
}

export default function MerchantApiKeysPage() {
  const [merchant, setMerchant] = useState(null)
  const [keys, setKeys] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  // Generate Key Modal states
  const [showGenerateModal, setShowGenerateModal] = useState(false)
  const [newKeyName, setNewKeyName] = useState('')
  const [generating, setGenerating] = useState(false)
  const [generateError, setGenerateError] = useState(null)

  // Ready Key Modal state (Image 3)
  const [createdResult, setCreatedResult] = useState(null)
  const [copied, setCopied] = useState(false)
  const [copiedSnippet, setCopiedSnippet] = useState(false)

  // Revoke Modal state
  const [keyToRevoke, setKeyToRevoke] = useState(null)
  const [revoking, setRevoking] = useState(false)
  const [revokeError, setRevokeError] = useState(null)

  const handleRetry = () => {
    setLoading(true)
    setError(null)
    merchantService.getMyMerchant()
      .then((merchantProfile) => {
        setMerchant(merchantProfile)
        if (merchantProfile?.status === 'APPROVED') {
          return merchantService.getApiKeys().then((keyList) => {
            setKeys(keyList || [])
            setLoading(false)
          })
        }
        setKeys([])
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
      .then((merchantProfile) => {
        if (!ignore) {
          setMerchant(merchantProfile)
          if (merchantProfile?.status === 'APPROVED') {
            return merchantService.getApiKeys(controller.signal).then((keyList) => {
              if (!ignore) {
                setKeys(keyList || [])
                setLoading(false)
              }
            })
          }
          setKeys([])
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

  const handleGenerateSubmit = async (e) => {
    e.preventDefault()
    if (!newKeyName.trim()) {
      setGenerateError('Key name is required.')
      return
    }

    setGenerating(true)
    setGenerateError(null)
    try {
      const result = await merchantService.createApiKey(newKeyName.trim())
      setCreatedResult({
        name: newKeyName.trim(),
        secretKey: result.secretKey,
      })
      setShowGenerateModal(false)
      setNewKeyName('')
      setCopied(false)
      // Refresh keys
      const updatedKeys = await merchantService.getApiKeys()
      setKeys(updatedKeys || [])
    } catch (err) {
      setGenerateError(err.message || 'Failed to generate API key.')
    } finally {
      setGenerating(false)
    }
  }

  const handleCopyKey = () => {
    if (createdResult?.secretKey) {
      navigator.clipboard.writeText(createdResult.secretKey)
      setCopied(true)
      setTimeout(() => setCopied(false), 2500)
    }
  }

  const handleCopySnippet = () => {
    navigator.clipboard.writeText(EXAMPLE_ORDER_CURL)
    setCopiedSnippet(true)
    setTimeout(() => setCopiedSnippet(false), 2500)
  }

  const handleRevokeConfirm = async () => {
    if (!keyToRevoke) return
    setRevoking(true)
    setRevokeError(null)
    try {
      await merchantService.revokeApiKey(keyToRevoke.id)
      setKeyToRevoke(null)
      // Refresh list
      const updatedKeys = await merchantService.getApiKeys()
      setKeys(updatedKeys || [])
    } catch (err) {
      setRevokeError(err.message || 'Failed to revoke API key.')
    } finally {
      setRevoking(false)
    }
  }

  const isApproved = merchant?.status === 'APPROVED'
  const activeKeys = keys.filter((k) => !k.revokedAt)

  return (
    <div className="merchant-scope merchant-apikeys">
      <nav className="page-breadcrumb" aria-label="Breadcrumb">
        <span>Workspace</span>
        <span>/</span>
        <strong>API keys</strong>
      </nav>

      <header className="page-header-row">
        <div>
          <h1 className="page-title">API key management</h1>
          <p className="page-subtitle">Securely connect your store, shipping tools and custom integrations.</p>
        </div>

        <div className="page-header-actions">
          <Button
            onClick={() => setShowGenerateModal(true)}
            disabled={!isApproved || loading}
            title={!isApproved ? 'Only approved merchants can generate API keys' : undefined}
          >
            + Generate New API Key
          </Button>
        </div>
      </header>

      {loading ? (
        <div className="card status-card status-card-loading" style={{ margin: '30px auto' }}>
          <LoadingSpinner />
          <p role="status">Loading API keys…</p>
        </div>
      ) : error ? (
        <div className="card status-card" role="alert" style={{ margin: '30px auto' }}>
          <h2 className="card-title">Unable to load API keys</h2>
          <p className="card-subtitle">{error.message || 'An error occurred while fetching API keys.'}</p>
          <Button onClick={handleRetry}>Try again</Button>
        </div>
      ) : (
        <>
          {/* Approval Status Banner */}
          {isApproved ? (
            <section className="info-banner info-banner-success" aria-label="API access status">
              <span className="info-banner-icon">
                <ShieldCheckIcon />
              </span>
              <div className="info-banner-content">
                <strong>Your business is approved for API access</strong>
                Keys are tied to {merchant?.businessName || 'your business'}. Only create keys for applications you trust.
              </div>
            </section>
          ) : (
            <section className="info-banner info-banner-warning" aria-label="API access status">
              <span className="info-banner-icon" aria-hidden="true">⚠️</span>
              <div className="info-banner-content">
                <strong>API key actions require business approval</strong>
                Your merchant account status is currently <strong>{merchant?.status || 'PENDING'}</strong>. API key generation is unlocked once an administrator approves your merchant application.
              </div>
            </section>
          )}

          {/* Main Table Card */}
          <section className="card-table" aria-label="API Keys List">
            <div className="card-table-header">
              <div className="card-table-title">
                Your API keys <span className="card-table-subtitle">{activeKeys.length} active {activeKeys.length === 1 ? 'key' : 'keys'}</span>
              </div>
              <span style={{ fontSize: '12px', color: 'var(--color-text-muted)' }}>
                Production environment
              </span>
            </div>

            <div className="table-wrapper">
              <table className="data-table">
                <thead>
                  <tr>
                    <th scope="col">Name</th>
                    <th scope="col">Key Prefix</th>
                    <th scope="col">Created Date</th>
                    <th scope="col">Last Used</th>
                    <th scope="col">Status</th>
                    <th scope="col">Action</th>
                  </tr>
                </thead>
                <tbody>
                  {keys.length === 0 ? (
                    <tr>
                      <td colSpan="6" className="table-empty-state">
                        <p className="table-empty-title">No API keys yet</p>
                        <p>
                          {isApproved
                            ? 'Generate your first API key above to start integrating with SwiftDrop APIs.'
                            : 'Once your business is approved, you will be able to generate and manage API keys here.'}
                        </p>
                      </td>
                    </tr>
                  ) : (
                    keys.map((key) => {
                      const isRevoked = Boolean(key.revokedAt)
                      return (
                        <tr key={key.id}>
                          <td style={{ fontWeight: 600 }}>{key.name}</td>
                          <td>
                            <code style={{ fontSize: '13px', color: 'var(--color-text-secondary)' }}>
                              {key.prefix}••••
                            </code>
                          </td>
                          <td>{formatDate(key.createdAt)}</td>
                          <td>{key.lastUsedAt ? formatDate(key.lastUsedAt) : 'Never'}</td>
                          <td>
                            {isRevoked ? (
                              <span className="badge badge-muted">Revoked</span>
                            ) : (
                              <span className="badge badge-success">Active</span>
                            )}
                          </td>
                          <td>
                            {!isRevoked && isApproved ? (
                              <button
                                type="button"
                                className="action-link action-link-danger"
                                onClick={() => {
                                  setKeyToRevoke(key)
                                  setRevokeError(null)
                                }}
                              >
                                Revoke
                              </button>
                            ) : (
                              <span style={{ color: 'var(--color-text-muted)' }}>—</span>
                            )}
                          </td>
                        </tr>
                      )
                    })
                  )}
                </tbody>
              </table>
            </div>

            <div className="card-table-footer">
              <span>Showing {keys.length} {keys.length === 1 ? 'key' : 'keys'}</span>
              <span>Revoked keys can no longer access your account.</span>
            </div>
          </section>

          {/* Bottom Info Cards */}
          <div className="grid-2col">
            <div className="info-card">
              <h3>Keep your keys secure</h3>
              <p>
                Treat API keys like passwords. Never share them publicly or include them in browser-side applications. Rotate keys regularly and revoke any you no longer use.
              </p>
            </div>

            <div className="info-card">
              <h3>Build your integration</h3>
              <p>
                Explore our API reference or get real-time order updates with webhook notifications.
              </p>
              <div className="info-card-links">
                <a href="#api-docs" className="action-link action-link-primary">
                  API documentation ↗
                </a>
                <Link to="/merchant/webhooks" className="action-link action-link-primary">
                  Webhook settings →
                </Link>
              </div>
            </div>
          </div>

          {/* Example Order Request Snippet */}
          <section className="code-card" aria-label="Example order request">
            <div className="code-header">
              <div>
                <h3 style={{ fontSize: '15px', fontWeight: 600, color: 'var(--color-text)', margin: '0 0 2px' }}>
                  Example Order Request
                </h3>
                <p style={{ fontSize: '13px', color: 'var(--color-text-secondary)', margin: 0 }}>
                  Authenticate order creation requests from your backend, warehouse, or e-commerce store using your API key.
                </p>
              </div>
              <span className="badge badge-info">cURL / REST API</span>
            </div>
            <div className="code-box">
              <button
                type="button"
                className="code-copy-btn"
                onClick={handleCopySnippet}
                aria-label="Copy cURL snippet"
              >
                {copiedSnippet ? '✓ Copied' : 'Copy cURL'}
              </button>
              <pre style={{ margin: 0, overflowX: 'auto' }}>
                <code>{EXAMPLE_ORDER_CURL}</code>
              </pre>
            </div>
          </section>
        </>
      )}

      {/* Generate API Key Modal */}
      {showGenerateModal && (
        <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="generate-modal-title">
          <div className="modal-dialog">
            <form onSubmit={handleGenerateSubmit}>
              <div className="modal-header">
                <div>
                  <h2 id="generate-modal-title" className="modal-title">Generate New API Key</h2>
                  <p className="modal-subtitle">Create a secret key for authenticating API requests</p>
                </div>
                <button
                  type="button"
                  className="modal-close-btn"
                  onClick={() => setShowGenerateModal(false)}
                  aria-label="Close dialog"
                >
                  ✕
                </button>
              </div>

              <div className="modal-body">
                {generateError && (
                  <div className="alert alert-error" role="alert">
                    {generateError}
                  </div>
                )}
                <FormField
                  id="api-key-name"
                  label="Key Name"
                  hint="Descriptive name to identify this key (e.g. Storefront integration, Warehouse sync)"
                  type="text"
                  value={newKeyName}
                  onChange={(e) => setNewKeyName(e.target.value)}
                  placeholder="e.g. Storefront integration"
                  autoFocus
                  required
                />
              </div>

              <div className="modal-footer">
                <Button variant="secondary" type="button" onClick={() => setShowGenerateModal(false)}>
                  Cancel
                </Button>
                <Button type="submit" disabled={generating}>
                  {generating ? 'Generating…' : 'Generate Key'}
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Key Ready Modal - Matching Figma Image 3 */}
      {createdResult && (
        <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="ready-modal-title">
          <div className="modal-dialog">
            <div className="modal-header" style={{ alignItems: 'center' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                <ReadyCheckIcon />
                <div>
                  <h2 id="ready-modal-title" className="modal-title">Your API key is ready</h2>
                  <p className="modal-subtitle">{createdResult.name} · Production</p>
                </div>
              </div>
            </div>

            <div className="modal-body">
              <div className="modal-notice-box" role="status">
                <strong>Copy this key now. You won't see it again.</strong>
                Store it securely. After you close this window, only the key prefix will be visible in your workspace.
              </div>

              <div className="key-copy-row">
                <label htmlFor="generated-key-field" className="form-label" style={{ fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--color-text-muted)' }}>
                  API KEY
                </label>
                <input
                  id="generated-key-field"
                  type="text"
                  readOnly
                  value={createdResult.secretKey}
                  className="input"
                  style={{
                    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
                    fontSize: '13px',
                    backgroundColor: '#f8fafc',
                  }}
                  onFocus={(e) => e.target.select()}
                />
                <div className="key-copy-actions">
                  <Button type="button" onClick={handleCopyKey}>
                    {copied ? '✓ Copied!' : 'Copy Key'}
                  </Button>
                  <span style={{ fontSize: '12px', color: 'var(--color-text-muted)' }}>
                    Save to your password or secrets manager.
                  </span>
                </div>
              </div>

              <p style={{ fontSize: '11px', color: 'var(--color-text-muted)', margin: 0 }}>
                Never include secret keys in client-side or public repositories. Illustrative key for this design — not a live credential.
              </p>
            </div>

            <div className="modal-footer">
              <Button type="button" onClick={() => setCreatedResult(null)}>
                I've saved my key
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Revoke Confirmation Modal */}
      {keyToRevoke && (
        <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="revoke-modal-title">
          <div className="modal-dialog">
            <div className="modal-header">
              <div>
                <h2 id="revoke-modal-title" className="modal-title">Revoke API Key</h2>
                <p className="modal-subtitle">Permanently disable API access for this credential</p>
              </div>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setKeyToRevoke(null)}
                aria-label="Close dialog"
              >
                ✕
              </button>
            </div>

            <div className="modal-body">
              {revokeError && (
                <div className="alert alert-error" role="alert">
                  {revokeError}
                </div>
              )}
              <p style={{ margin: 0 }}>
                Are you sure you want to revoke key <strong>"{keyToRevoke.name}"</strong> (prefix: <code>{keyToRevoke.prefix}</code>)?
              </p>
              <div className="modal-notice-box" style={{ backgroundColor: '#fef2f2', borderColor: '#fecaca', color: '#c81e1e' }}>
                Any applications, web stores, or background scripts using this key will immediately lose access. This action cannot be undone.
              </div>
            </div>

            <div className="modal-footer">
              <Button variant="secondary" type="button" onClick={() => setKeyToRevoke(null)}>
                Cancel
              </Button>
              <Button
                type="button"
                onClick={handleRevokeConfirm}
                disabled={revoking}
                style={{ backgroundColor: '#dc2626', borderColor: '#dc2626' }}
              >
                {revoking ? 'Revoking…' : 'Revoke Key'}
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}