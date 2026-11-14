import { useCallback, useEffect, useMemo, useState } from 'react'
import Alert from '../../components/Alert'
import Button from '../../components/Button'
import LoadingSpinner from '../../components/LoadingSpinner'
import { localityOptions, pricingService } from '../../services/pricingService'
import './pricing.css'

// The weight every route's base fee covers; one value for all routes. Used until
// rates are first published.
const DEFAULT_BASE_WEIGHT_KG = 1
const BASE_WEIGHT_FIELD = { min: 0, decimals: 3 }

const RATE_FIELDS = [
  { key: 'baseFee', label: 'Base fee (LKR)', min: 0, step: '0.01', decimals: 2 },
  { key: 'additionalKgFee', label: 'Each additional kg (LKR)', min: 0, step: '0.01', decimals: 2 },
  { key: 'expressMultiplier', label: 'Express ×', min: 1, step: '0.01', decimals: 2 },
]
const SIZES = [
  { key: 'small', label: 'Small' },
  { key: 'medium', label: 'Medium' },
  { key: 'large', label: 'Large' },
]

const text = (value) => (value === null || value === undefined ? '' : String(Number(value)))
const lkr = (value) => new Intl.NumberFormat('en-LK', {
  style: 'currency', currency: 'LKR', maximumFractionDigits: 2,
}).format(Number(value))
const dateLabel = (iso) => new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium' }).format(new Date(iso))

/**
 * The base weight published for the routes. It is set for all routes at once, so
 * routes normally agree; if older data does not, the most common value is shown.
 */
function publishedBaseWeight(card) {
  const counts = new Map()
  for (const route of card.routes) {
    if (route.baseWeightKg !== null && route.baseWeightKg !== undefined) {
      const value = text(route.baseWeightKg)
      counts.set(value, (counts.get(value) ?? 0) + 1)
    }
  }
  if (counts.size === 0) return String(DEFAULT_BASE_WEIGHT_KG)
  return [...counts.entries()].sort((a, b) => b[1] - a[1])[0][0]
}

/** The editable copy of a rate card: every value as the text in its input. */
function draftFrom(card) {
  return {
    baseWeight: publishedBaseWeight(card),
    routes: card.routes.map((route) => ({
      key: `${route.origin.id}:${route.destination.id}`,
      origin: route.origin,
      destination: route.destination,
      ...Object.fromEntries(RATE_FIELDS.map(({ key }) => [key, text(route[key])])),
    })),
    surcharges: Object.fromEntries(SIZES.map(({ key }) => [key, text(card.sizeSurcharges[key])])),
  }
}

function checkNumber(value, { min, decimals }, label) {
  const trimmed = value.trim()
  if (trimmed === '') return `${label} is required`
  const number = Number(trimmed)
  if (!Number.isFinite(number)) return `${label} must be a number`
  if (number < min) return min > 0 ? `${label} must be at least ${min}` : `${label} cannot be negative`
  const fraction = trimmed.split('.')[1]
  if (fraction && fraction.length > decimals) return `${label} allows ${decimals} decimal places`
  return null
}

/**
 * Errors keyed "<routeKey>.<field>" or "surcharge.<size>". A route row may be left
 * entirely blank (the route is not offered); otherwise every field is needed.
 */
function validateDraft(draft) {
  const errors = {}
  for (const route of draft.routes) {
    const values = RATE_FIELDS.map(({ key }) => route[key].trim())
    if (values.every((value) => value === '')) continue
    for (const field of RATE_FIELDS) {
      const message = checkNumber(route[field.key], field, field.label.replace(/ \(.*\)$/, ''))
      if (message) errors[`${route.key}.${field.key}`] = message
    }
  }
  const baseWeight = checkNumber(draft.baseWeight, BASE_WEIGHT_FIELD, 'Base weight')
  if (baseWeight) errors.baseWeight = baseWeight
  for (const { key, label } of SIZES) {
    const message = checkNumber(draft.surcharges[key], { min: 0, decimals: 2 }, `${label} surcharge`)
    if (message) errors[`surcharge.${key}`] = message
  }
  return errors
}

function publishBody(card, draft) {
  return {
    basedOnVersion: card.version,
    sizeSurcharges: Object.fromEntries(SIZES.map(({ key }) => [key, Number(draft.surcharges[key])])),
    routes: draft.routes
      .filter((route) => RATE_FIELDS.some(({ key }) => route[key].trim() !== ''))
      .map((route) => ({
        originZoneId: route.origin.id,
        destinationZoneId: route.destination.id,
        baseWeightKg: Number(draft.baseWeight),
        ...Object.fromEntries(RATE_FIELDS.map(({ key }) => [key, Number(route[key])])),
      })),
  }
}

function ZoneLabel({ zone }) {
  return <>{zone.name} <span className="pricing-zone-code">{zone.code}</span></>
}

export default function PricingPage() {
  const [card, setCard] = useState(null)
  const [draft, setDraft] = useState(null)
  const [zones, setZones] = useState([])
  const [loadError, setLoadError] = useState('')
  const [publishing, setPublishing] = useState(false)
  const [publishError, setPublishError] = useState(null)
  const [notice, setNotice] = useState('')
  const [showErrors, setShowErrors] = useState(false)

  const load = useCallback((signal) => Promise.all([
    pricingService.getRateCard({ signal }),
    pricingService.getZones({ signal }),
  ]).then(([rateCard, activeZones]) => {
    setCard(rateCard)
    setDraft(draftFrom(rateCard))
    setZones(activeZones)
    setLoadError('')
    setPublishError(null)
    setShowErrors(false)
  }).catch((error) => {
    if (error?.name !== 'AbortError') setLoadError(error?.message || 'Unable to load rates.')
  }), [])

  useEffect(() => {
    const controller = new AbortController()
    load(controller.signal)
    return () => controller.abort()
  }, [load])

  const pristine = useMemo(() => (card ? JSON.stringify(draftFrom(card)) : ''), [card])
  const dirty = draft !== null && JSON.stringify(draft) !== pristine
  const errors = useMemo(() => (draft ? validateDraft(draft) : {}), [draft])
  const errorCount = Object.keys(errors).length

  // Ask before leaving the page with unpublished changes.
  useEffect(() => {
    if (!dirty) return undefined
    const warn = (event) => { event.preventDefault() }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])

  const setRate = (routeKey, field, value) => setDraft((current) => ({
    ...current,
    routes: current.routes.map((route) => (route.key === routeKey ? { ...route, [field]: value } : route)),
  }))
  const setBaseWeight = (value) => setDraft((current) => ({ ...current, baseWeight: value }))
  const setSurcharge = (size, value) => setDraft((current) => ({
    ...current, surcharges: { ...current.surcharges, [size]: value },
  }))

  const discard = () => {
    setDraft(draftFrom(card))
    setShowErrors(false)
    setPublishError(null)
  }

  const publish = async () => {
    setShowErrors(true)
    if (errorCount > 0 || publishing) return
    setPublishing(true)
    setPublishError(null)
    setNotice('')
    try {
      const published = await pricingService.publishRates(publishBody(card, draft))
      setCard(published)
      setDraft(draftFrom(published))
      setShowErrors(false)
      setNotice(`Published rate version v${published.version}. New quotes use it from now on.`)
    } catch (error) {
      setPublishError({ message: error?.message || 'Unable to publish rates.', stale: error?.status === 409 })
    } finally {
      setPublishing(false)
    }
  }

  if (loadError && !card) {
    return (
      <div className="pricing-page">
        <h1 className="page-title">Pricing</h1>
        <div className="pricing-status" role="alert">
          <p><strong>Unable to load rates</strong></p>
          <p>{loadError}</p>
          <Button onClick={() => { setLoadError(''); load() }}>Try again</Button>
        </div>
      </div>
    )
  }
  if (!card || !draft) {
    return (
      <div className="pricing-page">
        <h1 className="page-title">Pricing</h1>
        <div className="pricing-status" role="status">
          <LoadingSpinner size="lg" label="Loading rates…" />
          <p>Loading rates…</p>
        </div>
      </div>
    )
  }

  const fieldError = (key) => (showErrors ? errors[key] : undefined)

  return (
    <div className="pricing-page pricing-page-wide">
      <header className="page-header-row">
        <div>
          <h1 className="page-title">Pricing</h1>
          <p className="page-subtitle">
            {card.version
              ? <>Published rate version <strong>v{card.version}</strong>, effective {dateLabel(card.publishedAt)}. </>
              : <>No rates are published yet, so customers cannot get quotes. </>}
            Existing parcels keep the fee they were booked with.
          </p>
          {dirty && <p className="pricing-draft-note">You have unpublished changes.</p>}
        </div>
        <div className="page-header-actions">
          <Button variant="outline" onClick={discard} disabled={!dirty || publishing}>Discard draft</Button>
          <Button onClick={publish} disabled={!dirty} loading={publishing} loadingText="Publishing…">
            Save and publish
          </Button>
        </div>
      </header>

      {notice && <Alert variant="success" onDismiss={() => setNotice('')}>{notice}</Alert>}
      {publishError && (
        <Alert variant="error" title="Not published" onDismiss={() => setPublishError(null)}>
          {publishError.message}{' '}
          {publishError.stale && (
            <button type="button" className="action-link action-link-primary" onClick={() => load()}>
              Reload latest rates
            </button>
          )}
        </Alert>
      )}
      {showErrors && errorCount > 0 && (
        <Alert variant="error">
          Fix the {errorCount === 1 ? 'highlighted value' : `${errorCount} highlighted values`} before publishing.
        </Alert>
      )}

      <div className="pricing-layout">
        <div className="pricing-main">
          <section className="pricing-card" aria-labelledby="route-rates-title">
            <div className="pricing-card-header">
              <h2 id="route-rates-title">Route rates</h2>
              <p>
                One row per pair of active zones; the price is the same in both directions.
                The base fee covers the base weight set below. Leave a row blank if you
                don&apos;t deliver on that route.
              </p>
            </div>
            {draft.routes.length === 0 ? (
              <p className="pricing-empty">Add active delivery zones first; their pairs appear here.</p>
            ) : (
              <div className="table-wrapper">
                <table className="data-table pricing-rate-table">
                  <thead>
                    <tr>
                      <th scope="col">Origin zone</th>
                      <th scope="col">Destination zone</th>
                      {RATE_FIELDS.map(({ key, label }) => <th key={key} scope="col">{label}</th>)}
                    </tr>
                  </thead>
                  <tbody>
                    {draft.routes.map((route) => {
                      const routeName = `${route.origin.name} to ${route.destination.name}`
                      return (
                        <tr key={route.key}>
                          <td><ZoneLabel zone={route.origin} /></td>
                          <td><ZoneLabel zone={route.destination} /></td>
                          {RATE_FIELDS.map((field) => {
                            const error = fieldError(`${route.key}.${field.key}`)
                            return (
                              <td key={field.key}>
                                <input className="input pricing-rate-input" type="number" inputMode="decimal"
                                  min={field.min} step={field.step} value={route[field.key]}
                                  placeholder={field.key === 'expressMultiplier' ? '1.5' : ''}
                                  aria-label={`${field.label} for ${routeName}`}
                                  aria-invalid={error ? true : undefined} title={error}
                                  onChange={(event) => setRate(route.key, field.key, event.target.value)} />
                              </td>
                            )
                          })}
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          <section className="pricing-card" aria-labelledby="base-weight-title">
            <div className="pricing-card-header">
              <h2 id="base-weight-title">Base weight</h2>
              <p>
                Included in every route&apos;s base fee. Weight above it is charged per started kg
                at the route&apos;s additional kg rate.
              </p>
            </div>
            <div className="pricing-surcharges">
              <div className="form-field">
                <label className="form-label" htmlFor="base-weight">Base weight (kg)</label>
                <div className="pricing-input-suffix">
                  <input id="base-weight" className="input" type="number" inputMode="decimal"
                    min="0" step="0.001" value={draft.baseWeight}
                    aria-invalid={fieldError('baseWeight') ? true : undefined}
                    aria-describedby={fieldError('baseWeight') ? 'base-weight-error' : undefined}
                    onChange={(event) => setBaseWeight(event.target.value)} />
                  <span aria-hidden="true">kg</span>
                </div>
                {fieldError('baseWeight') && (
                  <p id="base-weight-error" className="form-error">{fieldError('baseWeight')}</p>
                )}
              </div>
            </div>
          </section>

          <section className="pricing-card" aria-labelledby="size-surcharge-title">
            <div className="pricing-card-header">
              <h2 id="size-surcharge-title">Size surcharge</h2>
              <p>
                Added on top of the weight-based fee. Small ≤ 30 cm, Medium ≤ 60 cm,
                Large &gt; 60 cm (longest side).
              </p>
            </div>
            <div className="pricing-surcharges">
              {SIZES.map(({ key, label }) => {
                const error = fieldError(`surcharge.${key}`)
                return (
                  <div key={key} className="form-field">
                    <label className="form-label" htmlFor={`surcharge-${key}`}>{label}</label>
                    <div className="pricing-input-suffix">
                      <input id={`surcharge-${key}`} className="input" type="number" inputMode="decimal"
                        min="0" step="0.01" value={draft.surcharges[key]}
                        aria-invalid={error ? true : undefined}
                        aria-describedby={error ? `surcharge-${key}-error` : undefined}
                        onChange={(event) => setSurcharge(key, event.target.value)} />
                      <span aria-hidden="true">LKR</span>
                    </div>
                    {error && <p id={`surcharge-${key}-error`} className="form-error">{error}</p>}
                  </div>
                )
              })}
            </div>
          </section>

          <VersionHistory version={card.version} />
        </div>

        <QuotePreview zones={zones} version={card.version} />
      </div>
    </div>
  )
}

/** Earlier published versions, loaded when opened. */
function VersionHistory({ version }) {
  const [versions, setVersions] = useState(null)
  const [error, setError] = useState('')

  const toggle = (event) => {
    if (!event.currentTarget.open) return
    setError('')
    pricingService.getRateVersions()
      .then(setVersions)
      .catch((err) => setError(err?.message || 'Unable to load version history.'))
  }

  if (!version) return null
  return (
    // Reloads on every open, so a version published meanwhile shows up.
    <details className="pricing-card pricing-history" onToggle={toggle} key={version}>
      <summary>Version history</summary>
      {error && <p className="form-error">{error}</p>}
      {!versions && !error && <p className="pricing-muted">Loading…</p>}
      {versions && (
        <ul>
          {versions.map((item) => (
            <li key={item.version}>
              <strong>v{item.version}</strong> · {dateLabel(item.publishedAt)} ·{' '}
              {item.routeCount} {item.routeCount === 1 ? 'route' : 'routes'}
            </li>
          ))}
        </ul>
      )}
    </details>
  )
}

/** Prices a sample parcel with the published rates (not the unpublished draft). */
function QuotePreview({ zones, version }) {
  const options = useMemo(() => localityOptions(zones), [zones])
  const [input, setInput] = useState({ from: '', to: '', weight: '1', size: 'SMALL', serviceType: 'STANDARD' })
  const [result, setResult] = useState({ signature: '', quote: null, error: '' })

  const weight = Number(input.weight)
  const ready = input.from && input.to && Number.isFinite(weight) && weight >= 0.001
  const signature = JSON.stringify([input, version])

  useEffect(() => {
    if (!ready) return undefined
    const controller = new AbortController()
    const timer = setTimeout(() => {
      pricingService.getDeliveryQuote({
        pickupCity: input.from, deliveryCity: input.to, weight: input.weight,
        serviceType: input.serviceType, size: input.size,
      }, { signal: controller.signal })
        .then((quote) => setResult({ signature, quote, error: '' }))
        .catch((error) => {
          if (error?.name !== 'AbortError') {
            setResult({ signature, quote: null, error: error?.message || 'Unable to price this parcel.' })
          }
        })
    }, 300)
    return () => {
      clearTimeout(timer)
      controller.abort()
    }
  }, [ready, input, signature])

  const current = ready && result.signature === signature ? result : null
  const set = (key) => (event) => setInput((value) => ({ ...value, [key]: event.target.value }))
  const select = (id, label, key, choices) => (
    <div className="form-field">
      <label className="form-label" htmlFor={id}>{label}</label>
      <select id={id} className="input select" value={input[key]} onChange={set(key)}>
        {choices}
      </select>
    </div>
  )
  const localityChoices = <>
    <option value="">Select…</option>
    {options.map((group) => (
      <optgroup key={group.label} label={group.label}>
        {group.options.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
      </optgroup>
    ))}
  </>
  const { quote } = current ?? {}

  return (
    <aside className="pricing-card pricing-preview" aria-labelledby="quote-preview-title">
      <div className="pricing-card-header">
        <h2 id="quote-preview-title">Quote preview</h2>
        <p>Using published rates</p>
      </div>
      <div className="pricing-preview-fields">
        {select('preview-from', 'From', 'from', localityChoices)}
        {select('preview-to', 'To', 'to', localityChoices)}
        <div className="pricing-preview-pair">
          <div className="form-field">
            <label className="form-label" htmlFor="preview-weight">Weight</label>
            <div className="pricing-input-suffix">
              <input id="preview-weight" className="input" type="number" min="0.001" step="0.001"
                inputMode="decimal" value={input.weight} onChange={set('weight')} />
              <span aria-hidden="true">kg</span>
            </div>
          </div>
          {select('preview-size', 'Size', 'size', SIZES.map(({ key, label }) => (
            <option key={key} value={key.toUpperCase()}>{label}</option>
          )))}
        </div>
        {select('preview-service', 'Service', 'serviceType', <>
          <option value="STANDARD">Standard</option>
          <option value="EXPRESS">Express</option>
        </>)}
      </div>
      <div className="pricing-preview-result" aria-live="polite">
        {!ready ? (
          <p className="pricing-muted">Choose two cities and a weight to see the price.</p>
        ) : current?.error ? (
          <p className="form-error">{current.error}</p>
        ) : !quote ? (
          <p className="pricing-muted">Calculating…</p>
        ) : (
          <dl>
            <div><dt>Base</dt><dd>{lkr(quote.breakdown.baseFee)}</dd></div>
            <div><dt>Additional weight</dt><dd>{lkr(quote.breakdown.weightFee)}</dd></div>
            <div><dt>Size</dt><dd>{lkr(quote.breakdown.sizeFee)}</dd></div>
            {input.serviceType === 'EXPRESS' && (
              <div><dt>Express</dt><dd>{lkr(quote.breakdown.expressFee)}</dd></div>
            )}
            <div className="pricing-preview-total"><dt>Total</dt><dd>{lkr(quote.amount)}</dd></div>
          </dl>
        )}
      </div>
    </aside>
  )
}
