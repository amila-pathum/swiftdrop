import { useCallback, useEffect, useState } from 'react'
import Alert from '../../components/Alert'
import Button from '../../components/Button'
import FormField from '../../components/FormField'
import LoadingSpinner from '../../components/LoadingSpinner'
import { pricingService } from '../../services/pricingService'

const INITIAL_FORM_VALUES = {
  zoneId: '',
  baseFee: '',
  pricePerKg: '',
  expressMultiplier: '',
}

const INITIAL_EDIT_FORM_VALUES = {
  baseFee: '',
  pricePerKg: '',
  expressMultiplier: '',
}

const INITIAL_QUOTE_FORM_VALUES = {
  pickupCity: '',
  deliveryCity: '',
  weight: '',
  serviceType: '',
}

const SERVICE_TYPE_OPTIONS = [
  { value: 'STANDARD', label: 'STANDARD' },
  { value: 'EXPRESS', label: 'EXPRESS' },
]

function formatLKR(value) {
  if (value === null || value === undefined || value === '') return '—'
  const num = Number(value)
  if (Number.isNaN(num)) return '—'
  return `LKR ${num.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function formatMultiplier(value) {
  if (value === null || value === undefined || value === '') return '—'
  const num = Number(value)
  if (Number.isNaN(num)) return '—'
  return `${num.toFixed(2)}x`
}

function formatMoney(value, currency = 'LKR') {
  if (value === null || value === undefined || value === '') return '—'
  const num = Number(value)
  if (Number.isNaN(num)) return String(value)
  const formatted = num.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  return currency ? `${currency} ${formatted}` : formatted
}

function formatBreakdownLabel(key) {
  if (!key) return ''
  const formatted = key
    .replace(/_/g, ' ')
    .replace(/([A-Z])/g, ' $1')
    .trim()
  return formatted.charAt(0).toUpperCase() + formatted.slice(1)
}

export default function PricingRuleManagementPage() {
  const [rules, setRules] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [successMessage, setSuccessMessage] = useState('')

  // Add Pricing Rule Form state
  const [showAddModal, setShowAddModal] = useState(false)
  const [formValues, setFormValues] = useState(INITIAL_FORM_VALUES)
  const [formErrors, setFormErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState(null)
  const [zones, setZones] = useState([])
  const [loadingZones, setLoadingZones] = useState(false)

  // Edit Pricing Rule Form state
  const [editingRule, setEditingRule] = useState(null)
  const [editFormValues, setEditFormValues] = useState(INITIAL_EDIT_FORM_VALUES)
  const [editFormErrors, setEditFormErrors] = useState({})
  const [updating, setUpdating] = useState(false)
  const [updateError, setUpdateError] = useState(null)

  // Disable Pricing Rule state
  const [ruleToDisable, setRuleToDisable] = useState(null)
  const [disabling, setDisabling] = useState(false)
  const [disableError, setDisableError] = useState(null)

  // Try Pricing Quote state
  const [quoteForm, setQuoteForm] = useState(INITIAL_QUOTE_FORM_VALUES)
  const [quoteErrors, setQuoteErrors] = useState({})
  const [quoteLoading, setQuoteLoading] = useState(false)
  const [quoteError, setQuoteError] = useState(null)
  const [quoteResult, setQuoteResult] = useState(null)

  const loadRules = useCallback((options) => {
    return pricingService.getPricingRules(options)
      .then((data) => {
        setRules(Array.isArray(data) ? data : [])
        setError(null)
      })
      .catch((err) => {
        if (err?.name !== 'AbortError') {
          setError(err.message || 'Unable to load pricing rules. Please try again.')
        }
      })
      .finally(() => {
        setLoading(false)
      })
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    loadRules({ signal: controller.signal })
    return () => {
      controller.abort()
    }
  }, [loadRules])

  const handleRefresh = () => {
    setLoading(true)
    setError(null)
    loadRules()
  }

  // --- Add Pricing Rule Handlers ---
  const resetForm = useCallback(() => {
    setFormValues(INITIAL_FORM_VALUES)
    setFormErrors({})
    setSubmitError(null)
  }, [])

  const handleOpenAddModal = () => {
    resetForm()
    setSuccessMessage('')
    setShowAddModal(true)
    setLoadingZones(true)
    pricingService.getZones()
      .then((data) => {
        setZones(Array.isArray(data) ? data.filter((z) => z.active !== false) : [])
      })
      .catch((err) => {
        setSubmitError(err.message || 'Unable to load zones. Please try again.')
      })
      .finally(() => {
        setLoadingZones(false)
      })
  }

  const handleCloseAddModal = () => {
    if (!submitting) {
      setShowAddModal(false)
      resetForm()
    }
  }

  const handleFieldChange = (field, value) => {
    setFormValues((prev) => ({ ...prev, [field]: value }))
    if (formErrors[field]) {
      setFormErrors((prev) => ({ ...prev, [field]: undefined }))
    }
  }

  const handleAddSubmit = async (e) => {
    e.preventDefault()
    if (submitting) return

    const trimmedBaseFee = String(formValues.baseFee ?? '').trim()
    const trimmedPricePerKg = String(formValues.pricePerKg ?? '').trim()
    const trimmedMultiplier = String(formValues.expressMultiplier ?? '').trim()

    const errors = {}
    if (!formValues.zoneId) {
      errors.zoneId = 'Zone selection is required.'
    }

    if (!trimmedBaseFee) {
      errors.baseFee = 'Base fee is required.'
    } else {
      const num = Number(trimmedBaseFee)
      if (Number.isNaN(num)) {
        errors.baseFee = 'Base fee must be a valid number.'
      } else if (num < 0) {
        errors.baseFee = 'Base fee cannot be negative.'
      }
    }

    if (!trimmedPricePerKg) {
      errors.pricePerKg = 'Price per kg is required.'
    } else {
      const num = Number(trimmedPricePerKg)
      if (Number.isNaN(num)) {
        errors.pricePerKg = 'Price per kg must be a valid number.'
      } else if (num < 0) {
        errors.pricePerKg = 'Price per kg cannot be negative.'
      }
    }

    if (!trimmedMultiplier) {
      errors.expressMultiplier = 'Express multiplier is required.'
    } else {
      const num = Number(trimmedMultiplier)
      if (Number.isNaN(num)) {
        errors.expressMultiplier = 'Express multiplier must be a valid number.'
      } else if (num <= 0) {
        errors.expressMultiplier = 'Express multiplier must be greater than zero.'
      } else if (num < 1.0) {
        errors.expressMultiplier = 'Express multiplier must be at least 1.00.'
      }
    }

    if (Object.keys(errors).length > 0) {
      setFormErrors(errors)
      return
    }

    setSubmitting(true)
    setSubmitError(null)

    const payload = {
      zoneId: formValues.zoneId,
      baseFee: Number(trimmedBaseFee),
      pricePerKg: Number(trimmedPricePerKg),
      expressMultiplier: Number(trimmedMultiplier),
    }

    try {
      await pricingService.createPricingRule(payload)
      const selectedZone = zones.find((z) => z.id === formValues.zoneId)
      const zoneLabel = selectedZone ? selectedZone.name : 'the selected zone'
      setSuccessMessage(`Pricing rule for "${zoneLabel}" has been successfully created.`)
      setShowAddModal(false)
      resetForm()
      loadRules()
    } catch (err) {
      setSubmitError(err.message || 'Failed to create pricing rule. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  // --- Edit Pricing Rule Handlers ---
  const handleOpenEditModal = (rule) => {
    setEditingRule(rule)
    setEditFormValues({
      baseFee: rule.baseFee !== null && rule.baseFee !== undefined ? String(rule.baseFee) : '',
      pricePerKg: rule.pricePerKg !== null && rule.pricePerKg !== undefined ? String(rule.pricePerKg) : '',
      expressMultiplier: rule.expressMultiplier !== null && rule.expressMultiplier !== undefined ? String(rule.expressMultiplier) : '',
    })
    setEditFormErrors({})
    setUpdateError(null)
    setSuccessMessage('')
  }

  const handleCloseEditModal = useCallback(() => {
    if (!updating) {
      setEditingRule(null)
      setEditFormValues(INITIAL_EDIT_FORM_VALUES)
      setEditFormErrors({})
      setUpdateError(null)
    }
  }, [updating])

  const handleEditFieldChange = (field, value) => {
    setEditFormValues((prev) => ({ ...prev, [field]: value }))
    if (editFormErrors[field]) {
      setEditFormErrors((prev) => ({ ...prev, [field]: undefined }))
    }
  }

  const handleEditSubmit = async (e) => {
    e.preventDefault()
    if (updating || !editingRule) return

    const trimmedBaseFee = String(editFormValues.baseFee ?? '').trim()
    const trimmedPricePerKg = String(editFormValues.pricePerKg ?? '').trim()
    const trimmedMultiplier = String(editFormValues.expressMultiplier ?? '').trim()

    const errors = {}

    if (!trimmedBaseFee) {
      errors.baseFee = 'Base fee is required.'
    } else {
      const num = Number(trimmedBaseFee)
      if (Number.isNaN(num)) {
        errors.baseFee = 'Base fee must be a valid number.'
      } else if (num < 0) {
        errors.baseFee = 'Base fee cannot be negative.'
      }
    }

    if (!trimmedPricePerKg) {
      errors.pricePerKg = 'Price per kg is required.'
    } else {
      const num = Number(trimmedPricePerKg)
      if (Number.isNaN(num)) {
        errors.pricePerKg = 'Price per kg must be a valid number.'
      } else if (num < 0) {
        errors.pricePerKg = 'Price per kg cannot be negative.'
      }
    }

    if (!trimmedMultiplier) {
      errors.expressMultiplier = 'Express multiplier is required.'
    } else {
      const num = Number(trimmedMultiplier)
      if (Number.isNaN(num)) {
        errors.expressMultiplier = 'Express multiplier must be a valid number.'
      } else if (num <= 0) {
        errors.expressMultiplier = 'Express multiplier must be greater than zero.'
      } else if (num < 1.0) {
        errors.expressMultiplier = 'Express multiplier must be at least 1.00.'
      }
    }

    if (Object.keys(errors).length > 0) {
      setEditFormErrors(errors)
      return
    }

    setUpdating(true)
    setUpdateError(null)

    const payload = {
      baseFee: Number(trimmedBaseFee),
      pricePerKg: Number(trimmedPricePerKg),
      expressMultiplier: Number(trimmedMultiplier),
    }

    try {
      await pricingService.updatePricingRule(editingRule.id, payload)
      const zoneLabel = editingRule.zoneName || 'the selected zone'
      setSuccessMessage(`Pricing rule for "${zoneLabel}" has been successfully updated.`)
      setEditingRule(null)
      loadRules()
    } catch (err) {
      setUpdateError(err.message || 'Failed to update pricing rule. Please try again.')
    } finally {
      setUpdating(false)
    }
  }

  // --- Disable Pricing Rule Handlers ---
  const handleOpenDisableModal = (rule) => {
    setRuleToDisable(rule)
    setDisableError(null)
    setSuccessMessage('')
  }

  const handleCloseDisableModal = useCallback(() => {
    if (!disabling) {
      setRuleToDisable(null)
      setDisableError(null)
    }
  }, [disabling])

  const handleDisableConfirm = async () => {
    if (disabling || !ruleToDisable) return

    setDisabling(true)
    setDisableError(null)

    try {
      await pricingService.disablePricingRule(ruleToDisable.id)
      const zoneLabel = ruleToDisable.zoneName || 'the selected zone'
      setSuccessMessage(`Pricing rule for "${zoneLabel}" has been disabled.`)
      setRuleToDisable(null)
      loadRules()
    } catch (err) {
      setDisableError(err.message || 'Failed to disable pricing rule. Please try again.')
    } finally {
      setDisabling(false)
    }
  }

  // --- Try Pricing Quote Handlers ---
  const handleQuoteFieldChange = (field, value) => {
    setQuoteForm((prev) => ({ ...prev, [field]: value }))
    if (quoteErrors[field]) {
      setQuoteErrors((prev) => ({ ...prev, [field]: undefined }))
    }
    if (quoteResult) {
      setQuoteResult(null)
    }
    if (quoteError) {
      setQuoteError(null)
    }
  }

  const handleResetQuote = () => {
    setQuoteForm(INITIAL_QUOTE_FORM_VALUES)
    setQuoteErrors({})
    setQuoteError(null)
    setQuoteResult(null)
  }

  const handleQuoteSubmit = async (e) => {
    e.preventDefault()
    if (quoteLoading) return

    const trimmedPickupCity = String(quoteForm.pickupCity ?? '').trim()
    const trimmedDeliveryCity = String(quoteForm.deliveryCity ?? '').trim()
    const trimmedWeight = String(quoteForm.weight ?? '').trim()
    const selectedServiceType = String(quoteForm.serviceType ?? '').trim()

    const errors = {}

    if (!trimmedPickupCity) {
      errors.pickupCity = 'Pickup city is required.'
    }

    if (!trimmedDeliveryCity) {
      errors.deliveryCity = 'Delivery city is required.'
    }

    if (!trimmedWeight) {
      errors.weight = 'Parcel weight is required.'
    } else {
      const num = Number(trimmedWeight)
      if (Number.isNaN(num)) {
        errors.weight = 'Weight must be a valid number.'
      } else if (num <= 0) {
        errors.weight = 'Weight must be greater than 0 kg.'
      }
    }

    if (!selectedServiceType) {
      errors.serviceType = 'Service type is required.'
    } else if (selectedServiceType !== 'STANDARD' && selectedServiceType !== 'EXPRESS') {
      errors.serviceType = 'Service type must be either STANDARD or EXPRESS.'
    }

    if (Object.keys(errors).length > 0) {
      setQuoteErrors(errors)
      return
    }

    setQuoteLoading(true)
    setQuoteError(null)
    setQuoteResult(null)

    const payload = {
      pickupCity: trimmedPickupCity,
      deliveryCity: trimmedDeliveryCity,
      weight: Number(trimmedWeight),
      serviceType: selectedServiceType,
    }

    try {
      const data = await pricingService.calculateQuote(payload)
      setQuoteResult(data)
    } catch (err) {
      setQuoteResult(null)
      setQuoteError(
        err.message ||
          'Failed to calculate quote. Please verify that active delivery zones and pricing rules exist for these cities.'
      )
    } finally {
      setQuoteLoading(false)
    }
  }

  // Escape key handler for modals
  useEffect(() => {
    if (!showAddModal && !editingRule && !ruleToDisable) return
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') {
        if (showAddModal && !submitting) {
          setShowAddModal(false)
          resetForm()
        }
        if (editingRule && !updating) {
          handleCloseEditModal()
        }
        if (ruleToDisable && !disabling) {
          handleCloseDisableModal()
        }
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [
    showAddModal,
    submitting,
    editingRule,
    updating,
    ruleToDisable,
    disabling,
    resetForm,
    handleCloseEditModal,
    handleCloseDisableModal,
  ])

  return (
    <div className="pricing-rule-management-page">
      <nav className="page-breadcrumb" aria-label="Breadcrumb">
        <span>Admin workspace</span>
        <span>/</span>
        <strong>Pricing Rules</strong>
      </nav>

      <header className="page-header-row">
        <div>
          <h1 className="page-title">Pricing Rules</h1>
          <p className="page-subtitle">Configure base rates, weight charges, and delivery multipliers per zone.</p>
        </div>
        <div className="page-header-actions">
          <Button
            variant="secondary"
            onClick={handleRefresh}
            loading={loading && rules.length > 0}
            loadingText="Refreshing…"
            disabled={loading}
          >
            Refresh
          </Button>
          <Button onClick={handleOpenAddModal}>
            + Add Pricing Rule
          </Button>
        </div>
      </header>

      {successMessage && (
        <div style={{ marginBottom: '20px' }}>
          <Alert variant="success" onDismiss={() => setSuccessMessage('')}>
            {successMessage}
          </Alert>
        </div>
      )}

      {error && rules.length > 0 && (
        <div style={{ marginBottom: '20px' }}>
          <Alert variant="error" title="Unable to load pricing rules" onDismiss={() => setError(null)}>
            {error}
          </Alert>
        </div>
      )}

      {loading && rules.length === 0 ? (
        <div className="card status-card status-card-loading" style={{ margin: '30px auto', textAlign: 'center', padding: '40px 20px' }}>
          <LoadingSpinner size="lg" label="Loading pricing rules…" />
          <p role="status" style={{ marginTop: '12px', color: 'var(--color-text-secondary)' }}>
            Loading pricing rules…
          </p>
        </div>
      ) : error && rules.length === 0 ? (
        <div className="card status-card" role="alert" style={{ margin: '30px auto', textAlign: 'center', padding: '40px 20px' }}>
          <h2 className="card-title" style={{ fontSize: '18px', fontWeight: 600, marginBottom: '8px' }}>
            Unable to load pricing rules
          </h2>
          <p className="card-subtitle" style={{ color: 'var(--color-text-secondary)', marginBottom: '16px' }}>
            {error}
          </p>
          <Button onClick={handleRefresh}>Try again</Button>
        </div>
      ) : (
        <section className="card-table" aria-label="Pricing rules list">
          <div className="card-table-header">
            <div className="card-table-title">
              All Pricing Rules
              <span className="card-table-subtitle">
                {rules.length} {rules.length === 1 ? 'rule' : 'rules'}
              </span>
            </div>
          </div>

          <div className="table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th scope="col">Zone</th>
                  <th scope="col">Base Fee</th>
                  <th scope="col">Price Per Kg</th>
                  <th scope="col">Express Multiplier</th>
                  <th scope="col">Active Status</th>
                  <th scope="col">Actions</th>
                </tr>
              </thead>
              <tbody>
                {rules.length === 0 ? (
                  <tr>
                    <td colSpan="6" className="table-empty-state">
                      <p className="table-empty-title">No pricing rules available</p>
                      <p>There are no pricing rules configured yet.</p>
                    </td>
                  </tr>
                ) : (
                  rules.map((rule) => (
                    <tr key={rule.id || `${rule.zoneId}-${rule.baseFee}`}>
                      <td style={{ fontWeight: 600 }}>{rule.zoneName || rule.zoneId || '—'}</td>
                      <td>{formatLKR(rule.baseFee)}</td>
                      <td>{formatLKR(rule.pricePerKg)}</td>
                      <td>{formatMultiplier(rule.expressMultiplier)}</td>
                      <td>
                        {rule.active ? (
                          <span className="badge badge-success">Active</span>
                        ) : (
                          <span className="badge badge-muted">Inactive</span>
                        )}
                      </td>
                      <td>
                        <div className="actions-group">
                          <button
                            type="button"
                            className="action-link action-link-primary"
                            onClick={() => handleOpenEditModal(rule)}
                          >
                            Edit
                          </button>
                          {rule.active && (
                            <button
                              type="button"
                              className="action-link action-link-danger"
                              onClick={() => handleOpenDisableModal(rule)}
                            >
                              Disable
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          {rules.length > 0 && (
            <div className="card-table-footer">
              <span>
                Showing {rules.length} {rules.length === 1 ? 'rule' : 'rules'}
              </span>
            </div>
          )}
        </section>
      )}

      {/* Try Pricing Quote Section */}
      <section
        className="card-table quote-calculator-section"
        aria-labelledby="try-quote-heading"
        style={{ marginTop: '32px' }}
      >
        <div className="card-table-header">
          <div>
            <h2 id="try-quote-heading" className="card-table-title" style={{ margin: 0 }}>
              Try Pricing Quote
            </h2>
            <p className="card-table-subtitle" style={{ marginTop: '2px', display: 'block' }}>
              Estimate delivery costs based on active pricing rules for the destination zone.
            </p>
          </div>
        </div>

        <div style={{ padding: '24px' }}>
          {quoteError && (
            <div style={{ marginBottom: '20px' }}>
              <Alert variant="error" onDismiss={() => setQuoteError(null)}>
                {quoteError}
              </Alert>
            </div>
          )}

          <form onSubmit={handleQuoteSubmit} noValidate>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
                gap: '16px',
                marginBottom: '20px',
              }}
            >
              <FormField
                id="quote-pickup-city"
                label="Pickup City"
                placeholder="e.g. Colombo"
                value={quoteForm.pickupCity}
                onChange={(e) => handleQuoteFieldChange('pickupCity', e.target.value)}
                error={quoteErrors.pickupCity}
                required
                disabled={quoteLoading}
              />

              <FormField
                id="quote-delivery-city"
                label="Delivery City"
                placeholder="e.g. Kandy"
                value={quoteForm.deliveryCity}
                onChange={(e) => handleQuoteFieldChange('deliveryCity', e.target.value)}
                error={quoteErrors.deliveryCity}
                hint="Pricing is calculated from the delivery city's active zone rule"
                required
                disabled={quoteLoading}
              />

              <FormField
                id="quote-weight"
                label="Parcel Weight (kg)"
                type="number"
                step="0.01"
                min="0.001"
                placeholder="e.g. 2.5"
                value={quoteForm.weight}
                onChange={(e) => handleQuoteFieldChange('weight', e.target.value)}
                error={quoteErrors.weight}
                required
                disabled={quoteLoading}
              />

              <FormField
                as="select"
                id="quote-service-type"
                label="Service Type"
                value={quoteForm.serviceType}
                onChange={(e) => handleQuoteFieldChange('serviceType', e.target.value)}
                options={SERVICE_TYPE_OPTIONS}
                placeholder="Select service type…"
                error={quoteErrors.serviceType}
                required
                disabled={quoteLoading}
              />
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}>
              <Button
                type="submit"
                loading={quoteLoading}
                loadingText="Calculating Quote…"
                disabled={quoteLoading}
              >
                Calculate Quote
              </Button>
              {(quoteForm.pickupCity || quoteForm.deliveryCity || quoteForm.weight || quoteForm.serviceType || quoteResult || quoteError) && (
                <Button
                  variant="secondary"
                  type="button"
                  onClick={handleResetQuote}
                  disabled={quoteLoading}
                >
                  Clear Form
                </Button>
              )}
            </div>
          </form>

          {/* Quote Result Display */}
          {quoteResult && (
            <div
              className="quote-result-card"
              style={{
                marginTop: '24px',
                padding: '20px',
                backgroundColor: '#f8fafc',
                borderRadius: 'var(--radius-card, 6px)',
                border: '1px solid #e2e8f0',
              }}
            >
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'flex-start',
                  flexWrap: 'wrap',
                  gap: '16px',
                  marginBottom: '16px',
                  borderBottom: '1px solid #e2e8f0',
                  paddingBottom: '16px',
                }}
              >
                <div>
                  <span
                    style={{
                      fontSize: '12px',
                      fontWeight: 600,
                      color: '#64748b',
                      textTransform: 'uppercase',
                      letterSpacing: '0.05em',
                    }}
                  >
                    Estimated Total Charge
                  </span>
                  <div
                    style={{
                      fontSize: '28px',
                      fontWeight: 700,
                      color: '#0f172a',
                      marginTop: '4px',
                    }}
                  >
                    {formatMoney(quoteResult.charge, quoteResult.currency)}
                  </div>
                </div>
                <div style={{ textAlign: 'right' }}>
                  <span className="badge badge-success" style={{ fontSize: '12px' }}>
                    Quote Calculated
                  </span>
                  <div style={{ fontSize: '12px', color: '#64748b', marginTop: '6px' }}>
                    Currency: <strong>{quoteResult.currency || 'LKR'}</strong>
                  </div>
                </div>
              </div>

              {quoteResult.breakdown &&
                typeof quoteResult.breakdown === 'object' &&
                Object.keys(quoteResult.breakdown).length > 0 && (
                  <div>
                    <h3
                      style={{
                        fontSize: '13px',
                        fontWeight: 600,
                        color: '#334155',
                        margin: '0 0 12px 0',
                        textTransform: 'uppercase',
                        letterSpacing: '0.03em',
                      }}
                    >
                      Pricing Breakdown
                    </h3>
                    <div
                      style={{
                        display: 'grid',
                        gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
                        gap: '12px',
                      }}
                    >
                      {Object.entries(quoteResult.breakdown).map(([key, val]) => (
                        <div
                          key={key}
                          style={{
                            backgroundColor: '#ffffff',
                            border: '1px solid #e2e8f0',
                            borderRadius: '4px',
                            padding: '12px 14px',
                          }}
                        >
                          <div
                            style={{
                              fontSize: '12px',
                              color: '#64748b',
                              fontWeight: 500,
                              marginBottom: '4px',
                            }}
                          >
                            {formatBreakdownLabel(key)}
                          </div>
                          <div
                            style={{
                              fontSize: '16px',
                              fontWeight: 600,
                              color: '#0f172a',
                            }}
                          >
                            {formatMoney(val, quoteResult.currency)}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
            </div>
          )}
        </div>
      </section>

      {/* Add New Pricing Rule Modal */}
      {showAddModal && (
        <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="add-rule-modal-title">
          <div className="modal-dialog">
            <form onSubmit={handleAddSubmit} noValidate>
              <div className="modal-header">
                <div>
                  <h2 id="add-rule-modal-title" className="modal-title">Add Pricing Rule</h2>
                  <p className="modal-subtitle">Define rates and express multiplier for an active delivery zone</p>
                </div>
                <button
                  type="button"
                  className="modal-close-btn"
                  onClick={handleCloseAddModal}
                  aria-label="Close dialog"
                  disabled={submitting}
                >
                  ✕
                </button>
              </div>

              <div className="modal-body">
                {submitError && (
                  <Alert variant="error" onDismiss={() => setSubmitError(null)}>
                    {submitError}
                  </Alert>
                )}

                <FormField
                  as="select"
                  id="pricing-rule-zone"
                  label="Delivery Zone"
                  value={formValues.zoneId}
                  onChange={(e) => handleFieldChange('zoneId', e.target.value)}
                  options={zones.map((z) => ({
                    value: z.id,
                    label: `${z.name} (${z.city}, ${z.district})`,
                  }))}
                  placeholder={loadingZones ? 'Loading active zones…' : 'Select an active zone…'}
                  error={formErrors.zoneId}
                  required
                  autoFocus
                  disabled={submitting || loadingZones}
                />

                <FormField
                  id="pricing-rule-base-fee"
                  label="Base Fee (LKR)"
                  type="number"
                  step="0.01"
                  min="0"
                  placeholder="e.g. 250.00"
                  value={formValues.baseFee}
                  onChange={(e) => handleFieldChange('baseFee', e.target.value)}
                  error={formErrors.baseFee}
                  required
                  disabled={submitting}
                />

                <FormField
                  id="pricing-rule-price-per-kg"
                  label="Price Per Kg (LKR)"
                  type="number"
                  step="0.01"
                  min="0"
                  placeholder="e.g. 50.00"
                  value={formValues.pricePerKg}
                  onChange={(e) => handleFieldChange('pricePerKg', e.target.value)}
                  error={formErrors.pricePerKg}
                  required
                  disabled={submitting}
                />

                <FormField
                  id="pricing-rule-express-multiplier"
                  label="Express Multiplier"
                  type="number"
                  step="0.05"
                  min="1.00"
                  placeholder="e.g. 1.50"
                  value={formValues.expressMultiplier}
                  onChange={(e) => handleFieldChange('expressMultiplier', e.target.value)}
                  error={formErrors.expressMultiplier}
                  hint="Multiplier applied for express deliveries (must be at least 1.00)"
                  required
                  disabled={submitting}
                />
              </div>

              <div className="modal-footer">
                <Button
                  variant="secondary"
                  type="button"
                  onClick={handleCloseAddModal}
                  disabled={submitting}
                >
                  Cancel
                </Button>
                <Button
                  type="submit"
                  loading={submitting}
                  loadingText="Creating Rule…"
                  disabled={submitting || loadingZones}
                >
                  Create Rule
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Edit Pricing Rule Modal */}
      {editingRule && (
        <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="edit-rule-modal-title">
          <div className="modal-dialog">
            <form onSubmit={handleEditSubmit} noValidate>
              <div className="modal-header">
                <div>
                  <h2 id="edit-rule-modal-title" className="modal-title">Edit Pricing Rule</h2>
                  <p className="modal-subtitle">Update base rates and delivery multiplier for {editingRule.zoneName || 'this zone'}</p>
                </div>
                <button
                  type="button"
                  className="modal-close-btn"
                  onClick={handleCloseEditModal}
                  aria-label="Close dialog"
                  disabled={updating}
                >
                  ✕
                </button>
              </div>

              <div className="modal-body">
                {updateError && (
                  <Alert variant="error" onDismiss={() => setUpdateError(null)}>
                    {updateError}
                  </Alert>
                )}

                <FormField
                  id="edit-pricing-rule-zone"
                  label="Delivery Zone"
                  value={editingRule.zoneName || editingRule.zoneId || '—'}
                  disabled
                  readOnly
                  hint="The delivery zone for an existing pricing rule cannot be changed."
                />

                <FormField
                  id="edit-pricing-rule-base-fee"
                  label="Base Fee (LKR)"
                  type="number"
                  step="0.01"
                  min="0"
                  placeholder="e.g. 250.00"
                  value={editFormValues.baseFee}
                  onChange={(e) => handleEditFieldChange('baseFee', e.target.value)}
                  error={editFormErrors.baseFee}
                  required
                  autoFocus
                  disabled={updating}
                />

                <FormField
                  id="edit-pricing-rule-price-per-kg"
                  label="Price Per Kg (LKR)"
                  type="number"
                  step="0.01"
                  min="0"
                  placeholder="e.g. 50.00"
                  value={editFormValues.pricePerKg}
                  onChange={(e) => handleEditFieldChange('pricePerKg', e.target.value)}
                  error={editFormErrors.pricePerKg}
                  required
                  disabled={updating}
                />

                <FormField
                  id="edit-pricing-rule-express-multiplier"
                  label="Express Multiplier"
                  type="number"
                  step="0.05"
                  min="1.00"
                  placeholder="e.g. 1.50"
                  value={editFormValues.expressMultiplier}
                  onChange={(e) => handleEditFieldChange('expressMultiplier', e.target.value)}
                  error={editFormErrors.expressMultiplier}
                  hint="Multiplier applied for express deliveries (must be at least 1.00)"
                  required
                  disabled={updating}
                />
              </div>

              <div className="modal-footer">
                <Button
                  variant="secondary"
                  type="button"
                  onClick={handleCloseEditModal}
                  disabled={updating}
                >
                  Cancel
                </Button>
                <Button
                  type="submit"
                  loading={updating}
                  loadingText="Saving Changes…"
                  disabled={updating}
                >
                  Save Changes
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Disable Pricing Rule Confirmation Modal */}
      {ruleToDisable && (
        <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="disable-rule-modal-title">
          <div className="modal-dialog">
            <div className="modal-header">
              <div>
                <h2 id="disable-rule-modal-title" className="modal-title">Disable Pricing Rule</h2>
                <p className="modal-subtitle">Deactivate pricing rates for this delivery zone</p>
              </div>
              <button
                type="button"
                className="modal-close-btn"
                onClick={handleCloseDisableModal}
                aria-label="Close dialog"
                disabled={disabling}
              >
                ✕
              </button>
            </div>

            <div className="modal-body">
              {disableError && (
                <Alert variant="error" onDismiss={() => setDisableError(null)}>
                  {disableError}
                </Alert>
              )}

              <p style={{ margin: 0 }}>
                Are you sure you want to disable the pricing rule for <strong>{ruleToDisable.zoneName || 'this zone'}</strong>?
              </p>

              <div
                className="modal-notice-box"
                style={{
                  backgroundColor: '#fffbeb',
                  borderColor: '#fde68a',
                  color: '#92400e',
                }}
              >
                Disabling this pricing rule will make it inactive for delivery quotes. This does not permanently delete the pricing rule or its historical data.
              </div>
            </div>

            <div className="modal-footer">
              <Button
                variant="secondary"
                type="button"
                onClick={handleCloseDisableModal}
                disabled={disabling}
              >
                Cancel
              </Button>
              <Button
                type="button"
                loading={disabling}
                loadingText="Disabling Rule…"
                disabled={disabling}
                onClick={handleDisableConfirm}
                style={{ backgroundColor: '#dc2626', borderColor: '#dc2626' }}
              >
                Disable Rule
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
