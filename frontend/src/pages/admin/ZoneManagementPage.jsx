import { useCallback, useEffect, useState } from 'react'
import Alert from '../../components/Alert'
import Button from '../../components/Button'
import Checkbox from '../../components/Checkbox'
import FormField from '../../components/FormField'
import LoadingSpinner from '../../components/LoadingSpinner'
import { pricingService } from '../../services/pricingService'
import './pricing.css'

const EMPTY_FORM = { name: '', code: '', localities: '', active: true }

/** "Kandy, Peradeniya,, kandy" -> ['Kandy', 'Peradeniya'] (trimmed, repeats dropped). */
function parseLocalities(text) {
  const seen = new Set()
  return text.split(/[,\n]/)
    .map((value) => value.trim().replace(/\s+/g, ' '))
    .filter((value) => value && !seen.has(value.toLowerCase()) && seen.add(value.toLowerCase()))
}

function validate(form) {
  const errors = {}
  const name = form.name.trim()
  if (!name) errors.name = 'Zone name is required.'
  else if (name.length > 100) errors.name = 'Zone name must not exceed 100 characters.'

  const code = form.code.trim()
  if (!code) errors.code = 'Zone code is required.'
  else if (!/^[A-Za-z0-9]{1,10}$/.test(code)) errors.code = 'Use 1 to 10 letters or digits.'

  const localities = parseLocalities(form.localities)
  if (localities.length === 0) errors.localities = 'Add at least one city this zone covers.'
  else if (localities.some((locality) => locality.length > 100)) {
    errors.localities = 'Each city must not exceed 100 characters.'
  }
  return errors
}

/** Server field names such as "localities[2]" belong to the localities box. */
function fieldErrors(fields = {}) {
  return Object.fromEntries(Object.entries(fields)
    .map(([key, message]) => [key.replace(/\[\d+\]$/, ''), message]))
}

export default function ZoneManagementPage() {
  const [zones, setZones] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  // null = closed; { id: null } = adding; { id } = editing that zone.
  const [editing, setEditing] = useState(null)
  const [form, setForm] = useState(EMPTY_FORM)
  const [formErrors, setFormErrors] = useState({})
  const [saveError, setSaveError] = useState('')
  const [saving, setSaving] = useState(false)

  const load = useCallback((options) => pricingService.getZones({ ...options, includeInactive: true })
    .then((data) => {
      setZones(Array.isArray(data) ? data : [])
      setError('')
    })
    .catch((err) => {
      if (err?.name !== 'AbortError') setError(err?.message || 'Unable to load delivery zones.')
    })
    .finally(() => setLoading(false)), [])

  useEffect(() => {
    const controller = new AbortController()
    load({ signal: controller.signal })
    return () => controller.abort()
  }, [load])

  const close = useCallback(() => {
    if (!saving) setEditing(null)
  }, [saving])

  useEffect(() => {
    if (!editing) return undefined
    const onKey = (event) => { if (event.key === 'Escape') close() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [editing, close])

  const open = (zone) => {
    setEditing({ id: zone?.id ?? null })
    setForm(zone
      ? { name: zone.name, code: zone.code, localities: zone.localities.join(', '), active: zone.active }
      : EMPTY_FORM)
    setFormErrors({})
    setSaveError('')
    setNotice('')
  }

  const change = (key, value) => {
    setForm((current) => ({ ...current, [key]: value }))
    setFormErrors((current) => ({ ...current, [key]: undefined }))
  }

  const save = async (event) => {
    event.preventDefault()
    const found = validate(form)
    setFormErrors(found)
    if (Object.keys(found).length > 0) return

    const body = {
      name: form.name.trim(),
      code: form.code.trim().toUpperCase(),
      localities: parseLocalities(form.localities),
      active: form.active,
    }
    setSaving(true)
    setSaveError('')
    try {
      if (editing.id) await pricingService.updateZone(editing.id, body)
      else await pricingService.createZone(body)
      setNotice(`${body.name} was ${editing.id ? 'updated' : 'added'}.`)
      setEditing(null)
      await load()
    } catch (err) {
      setFormErrors(fieldErrors(err?.fields))
      setSaveError(err?.message || 'Unable to save this zone.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="pricing-page">
      <header className="page-header-row">
        <div>
          <h1 className="page-title">Delivery Zones</h1>
          <p className="page-subtitle">
            Each zone covers a list of cities. Customers can book between any cities in active zones.
          </p>
        </div>
        <div className="page-header-actions">
          <Button onClick={() => open(null)}>+ Add zone</Button>
        </div>
      </header>

      {notice && <Alert variant="success" onDismiss={() => setNotice('')}>{notice}</Alert>}
      {error && zones.length > 0 && <Alert variant="error" onDismiss={() => setError('')}>{error}</Alert>}

      {loading ? (
        <div className="pricing-status" role="status">
          <LoadingSpinner size="lg" label="Loading delivery zones…" />
          <p>Loading delivery zones…</p>
        </div>
      ) : error && zones.length === 0 ? (
        <div className="pricing-status" role="alert">
          <p><strong>Unable to load delivery zones</strong></p>
          <p>{error}</p>
          <Button onClick={() => { setLoading(true); load() }}>Try again</Button>
        </div>
      ) : (
        <section className="pricing-card" aria-label="Delivery zones">
          <div className="table-wrapper">
            <table className="data-table pricing-zone-table">
              <thead>
                <tr>
                  <th scope="col">Zone name</th>
                  <th scope="col">Code</th>
                  <th scope="col">Covered localities</th>
                  <th scope="col">State</th>
                  <th scope="col"><span className="visually-hidden">Actions</span></th>
                </tr>
              </thead>
              <tbody>
                {zones.length === 0 ? (
                  <tr>
                    <td colSpan="5" className="table-empty-state">
                      <p className="table-empty-title">No zones yet</p>
                      <p>Add a zone and the cities it covers to start taking bookings.</p>
                    </td>
                  </tr>
                ) : zones.map((zone) => (
                  <tr key={zone.id}>
                    <td className="pricing-zone-name">{zone.name}</td>
                    <td><code className="pricing-code">{zone.code}</code></td>
                    <td>{zone.localities.join(', ')}</td>
                    <td>
                      <span className={`badge ${zone.active ? 'badge-success' : 'badge-muted'}`}>
                        {zone.active ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td className="pricing-row-action">
                      <Button variant="outline" size="sm" onClick={() => open(zone)}
                        aria-label={`Edit ${zone.name}`}>
                        Edit
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {editing && (
        <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="zone-dialog-title">
          <div className="modal-dialog">
            <form onSubmit={save} noValidate>
              <div className="modal-header">
                <h2 id="zone-dialog-title" className="modal-title">{editing.id ? 'Edit zone' : 'Add zone'}</h2>
                <button type="button" className="modal-close-btn" onClick={close}
                  aria-label="Close dialog" disabled={saving}>✕</button>
              </div>

              <div className="modal-body">
                {saveError && <Alert variant="error" onDismiss={() => setSaveError('')}>{saveError}</Alert>}
                <div className="pricing-zone-form-row">
                  <FormField id="zone-name" label="Zone name" value={form.name} maxLength={100}
                    onChange={(e) => change('name', e.target.value)} error={formErrors.name}
                    required autoFocus disabled={saving} />
                  <FormField id="zone-code" label="Zone code" value={form.code} maxLength={10}
                    className="pricing-zone-code-field"
                    onChange={(e) => change('code', e.target.value.toUpperCase())}
                    error={formErrors.code} required disabled={saving} />
                </div>
                <FormField id="zone-localities" as="textarea" label="Covered localities"
                  placeholder="Comma-separated, e.g. Kandy, Peradeniya" value={form.localities}
                  onChange={(e) => change('localities', e.target.value)} error={formErrors.localities}
                  hint="Customers choose these cities when booking. A city can belong to only one zone."
                  required disabled={saving} />
                <Checkbox id="zone-active" label="Zone is active for bookings" checked={form.active}
                  onChange={(e) => change('active', e.target.checked)} disabled={saving} />
              </div>

              <div className="modal-footer">
                <Button variant="outline" onClick={close} disabled={saving}>Cancel</Button>
                <Button type="submit" loading={saving} loadingText="Saving…">Save zone</Button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
