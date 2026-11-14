import { useCallback, useEffect, useState } from 'react'
import Alert from '../../components/Alert'
import Button from '../../components/Button'
import FormField from '../../components/FormField'
import LoadingSpinner from '../../components/LoadingSpinner'
import { pricingService } from '../../services/pricingService'

const INITIAL_FORM_VALUES = {
  name: '',
  city: '',
  district: '',
}

export default function ZoneManagementPage() {
  const [zones, setZones] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [successMessage, setSuccessMessage] = useState('')

  // Add Zone Form state
  const [showAddModal, setShowAddModal] = useState(false)
  const [formValues, setFormValues] = useState(INITIAL_FORM_VALUES)
  const [formErrors, setFormErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState(null)

  // Edit Zone Form state
  const [editingZone, setEditingZone] = useState(null)
  const [editFormValues, setEditFormValues] = useState(INITIAL_FORM_VALUES)
  const [editFormErrors, setEditFormErrors] = useState({})
  const [updating, setUpdating] = useState(false)
  const [updateError, setUpdateError] = useState(null)

  // Disable Zone state
  const [zoneToDisable, setZoneToDisable] = useState(null)
  const [disabling, setDisabling] = useState(false)
  const [disableError, setDisableError] = useState(null)

  const loadZones = useCallback((options) => {
    return pricingService.getZones(options)
      .then((data) => {
        setZones(Array.isArray(data) ? data : [])
        setError(null)
      })
      .catch((err) => {
        if (err?.name !== 'AbortError') {
          setError(err.message || 'Unable to load delivery zones. Please try again.')
        }
      })
      .finally(() => {
        setLoading(false)
      })
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    loadZones({ signal: controller.signal })
    return () => {
      controller.abort()
    }
  }, [loadZones])

  const handleRefresh = () => {
    setLoading(true)
    setError(null)
    loadZones()
  }

  // --- Add Zone Handlers ---
  const resetForm = useCallback(() => {
    setFormValues(INITIAL_FORM_VALUES)
    setFormErrors({})
    setSubmitError(null)
  }, [])

  const handleOpenAddModal = () => {
    resetForm()
    setSuccessMessage('')
    setShowAddModal(true)
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

    const name = formValues.name.trim()
    const city = formValues.city.trim()
    const district = formValues.district.trim()

    const errors = {}
    if (!name) {
      errors.name = 'Zone name is required.'
    }
    if (!city) {
      errors.city = 'City is required.'
    }
    if (!district) {
      errors.district = 'District is required.'
    }

    if (Object.keys(errors).length > 0) {
      setFormErrors(errors)
      return
    }

    setSubmitting(true)
    setSubmitError(null)

    try {
      await pricingService.createZone({ name, city, district })
      setSuccessMessage(`Zone "${name}" has been successfully created.`)
      setShowAddModal(false)
      resetForm()
      loadZones()
    } catch (err) {
      setSubmitError(err.message || 'Failed to create zone. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  // --- Edit Zone Handlers ---
  const handleOpenEditModal = (zone) => {
    setEditingZone(zone)
    setEditFormValues({
      name: zone.name || '',
      city: zone.city || '',
      district: zone.district || '',
    })
    setEditFormErrors({})
    setUpdateError(null)
    setSuccessMessage('')
  }

  const handleCloseEditModal = useCallback(() => {
    if (!updating) {
      setEditingZone(null)
      setEditFormValues(INITIAL_FORM_VALUES)
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
    if (updating || !editingZone) return

    const name = editFormValues.name.trim()
    const city = editFormValues.city.trim()
    const district = editFormValues.district.trim()

    const errors = {}
    if (!name) {
      errors.name = 'Zone name is required.'
    }
    if (!city) {
      errors.city = 'City is required.'
    }
    if (!district) {
      errors.district = 'District is required.'
    }

    if (Object.keys(errors).length > 0) {
      setEditFormErrors(errors)
      return
    }

    setUpdating(true)
    setUpdateError(null)

    try {
      await pricingService.updateZone(editingZone.id, { name, city, district })
      setSuccessMessage(`Zone "${name}" has been successfully updated.`)
      setEditingZone(null)
      loadZones()
    } catch (err) {
      setUpdateError(err.message || 'Failed to update zone. Please try again.')
    } finally {
      setUpdating(false)
    }
  }

  // --- Disable Zone Handlers ---
  const handleOpenDisableModal = (zone) => {
    setZoneToDisable(zone)
    setDisableError(null)
    setSuccessMessage('')
  }

  const handleCloseDisableModal = useCallback(() => {
    if (!disabling) {
      setZoneToDisable(null)
      setDisableError(null)
    }
  }, [disabling])

  const handleDisableConfirm = async () => {
    if (disabling || !zoneToDisable) return

    setDisabling(true)
    setDisableError(null)

    try {
      await pricingService.disableZone(zoneToDisable.id)
      setSuccessMessage(`Zone "${zoneToDisable.name}" has been disabled.`)
      setZoneToDisable(null)
      loadZones()
    } catch (err) {
      setDisableError(err.message || 'Failed to disable zone. Please try again.')
    } finally {
      setDisabling(false)
    }
  }

  // Escape key handler for modals
  useEffect(() => {
    if (!showAddModal && !editingZone && !zoneToDisable) return
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') {
        if (showAddModal && !submitting) {
          setShowAddModal(false)
          resetForm()
        }
        if (editingZone && !updating) {
          handleCloseEditModal()
        }
        if (zoneToDisable && !disabling) {
          handleCloseDisableModal()
        }
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [
    showAddModal,
    submitting,
    editingZone,
    updating,
    zoneToDisable,
    disabling,
    resetForm,
    handleCloseEditModal,
    handleCloseDisableModal,
  ])

  return (
    <div className="zone-management-page">
      <nav className="page-breadcrumb" aria-label="Breadcrumb">
        <span>Admin workspace</span>
        <span>/</span>
        <strong>Zone Management</strong>
      </nav>

      <header className="page-header-row">
        <div>
          <h1 className="page-title">Delivery Zones</h1>
          <p className="page-subtitle">View and manage delivery zones and geographical coverage for SwiftDrop.</p>
        </div>
        <div className="page-header-actions">
          <Button
            variant="secondary"
            onClick={handleRefresh}
            loading={loading && zones.length > 0}
            loadingText="Refreshing…"
            disabled={loading}
          >
            Refresh
          </Button>
          <Button onClick={handleOpenAddModal}>
            + Add New Zone
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

      {error && (
        <div style={{ marginBottom: '20px' }}>
          <Alert variant="error" title="Unable to load zones" onDismiss={() => setError(null)}>
            {error}
          </Alert>
        </div>
      )}

      {loading && zones.length === 0 ? (
        <div className="card status-card status-card-loading" style={{ margin: '30px auto', textAlign: 'center', padding: '40px 20px' }}>
          <LoadingSpinner size="lg" label="Loading delivery zones…" />
          <p role="status" style={{ marginTop: '12px', color: 'var(--color-text-secondary)' }}>
            Loading delivery zones…
          </p>
        </div>
      ) : error && zones.length === 0 ? (
        <div className="card status-card" role="alert" style={{ margin: '30px auto', textAlign: 'center', padding: '40px 20px' }}>
          <h2 className="card-title" style={{ fontSize: '18px', fontWeight: 600, marginBottom: '8px' }}>
            Unable to load delivery zones
          </h2>
          <p className="card-subtitle" style={{ color: 'var(--color-text-secondary)', marginBottom: '16px' }}>
            {error}
          </p>
          <Button onClick={handleRefresh}>Try again</Button>
        </div>
      ) : (
        <section className="card-table" aria-label="Delivery zones list">
          <div className="card-table-header">
            <div className="card-table-title">
              All Delivery Zones
              <span className="card-table-subtitle">
                {zones.length} {zones.length === 1 ? 'zone' : 'zones'}
              </span>
            </div>
          </div>

          <div className="table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th scope="col">Zone Name</th>
                  <th scope="col">City</th>
                  <th scope="col">District</th>
                  <th scope="col">Active Status</th>
                  <th scope="col">Actions</th>
                </tr>
              </thead>
              <tbody>
                {zones.length === 0 ? (
                  <tr>
                    <td colSpan="5" className="table-empty-state">
                      <p className="table-empty-title">No zones available</p>
                      <p>There are no delivery zones configured yet.</p>
                    </td>
                  </tr>
                ) : (
                  zones.map((zone) => (
                    <tr key={zone.id || `${zone.name}-${zone.city}`}>
                      <td style={{ fontWeight: 600 }}>{zone.name || '—'}</td>
                      <td>{zone.city || '—'}</td>
                      <td>{zone.district || '—'}</td>
                      <td>
                        {zone.active ? (
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
                            onClick={() => handleOpenEditModal(zone)}
                          >
                            Edit
                          </button>
                          {zone.active && (
                            <button
                              type="button"
                              className="action-link action-link-danger"
                              onClick={() => handleOpenDisableModal(zone)}
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

          {zones.length > 0 && (
            <div className="card-table-footer">
              <span>
                Showing {zones.length} {zones.length === 1 ? 'zone' : 'zones'}
              </span>
            </div>
          )}
        </section>
      )}

      {/* Add New Zone Modal */}
      {showAddModal && (
        <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="add-zone-modal-title">
          <div className="modal-dialog">
            <form onSubmit={handleAddSubmit} noValidate>
              <div className="modal-header">
                <div>
                  <h2 id="add-zone-modal-title" className="modal-title">Add New Delivery Zone</h2>
                  <p className="modal-subtitle">Define a new coverage area for delivery services</p>
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
                  id="zone-name"
                  label="Zone Name"
                  placeholder="e.g. Colombo Central"
                  value={formValues.name}
                  onChange={(e) => handleFieldChange('name', e.target.value)}
                  error={formErrors.name}
                  required
                  autoFocus
                  disabled={submitting}
                />

                <FormField
                  id="zone-city"
                  label="City"
                  placeholder="e.g. Colombo"
                  value={formValues.city}
                  onChange={(e) => handleFieldChange('city', e.target.value)}
                  error={formErrors.city}
                  required
                  disabled={submitting}
                />

                <FormField
                  id="zone-district"
                  label="District"
                  placeholder="e.g. Colombo"
                  value={formValues.district}
                  onChange={(e) => handleFieldChange('district', e.target.value)}
                  error={formErrors.district}
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
                  loadingText="Creating Zone…"
                  disabled={submitting}
                >
                  Create Zone
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Edit Zone Modal */}
      {editingZone && (
        <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="edit-zone-modal-title">
          <div className="modal-dialog">
            <form onSubmit={handleEditSubmit} noValidate>
              <div className="modal-header">
                <div>
                  <h2 id="edit-zone-modal-title" className="modal-title">Edit Delivery Zone</h2>
                  <p className="modal-subtitle">Update geographical coverage information for this zone</p>
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
                  id="edit-zone-name"
                  label="Zone Name"
                  placeholder="e.g. Colombo Central"
                  value={editFormValues.name}
                  onChange={(e) => handleEditFieldChange('name', e.target.value)}
                  error={editFormErrors.name}
                  required
                  autoFocus
                  disabled={updating}
                />

                <FormField
                  id="edit-zone-city"
                  label="City"
                  placeholder="e.g. Colombo"
                  value={editFormValues.city}
                  onChange={(e) => handleEditFieldChange('city', e.target.value)}
                  error={editFormErrors.city}
                  required
                  disabled={updating}
                />

                <FormField
                  id="edit-zone-district"
                  label="District"
                  placeholder="e.g. Colombo"
                  value={editFormValues.district}
                  onChange={(e) => handleEditFieldChange('district', e.target.value)}
                  error={editFormErrors.district}
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

      {/* Disable Zone Confirmation Modal */}
      {zoneToDisable && (
        <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="disable-zone-modal-title">
          <div className="modal-dialog">
            <div className="modal-header">
              <div>
                <h2 id="disable-zone-modal-title" className="modal-title">Disable Delivery Zone</h2>
                <p className="modal-subtitle">Deactivate delivery coverage for this zone</p>
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
                Are you sure you want to disable <strong>{zoneToDisable.name}</strong> ({zoneToDisable.city}, {zoneToDisable.district})?
              </p>

              <div
                className="modal-notice-box"
                style={{
                  backgroundColor: '#fffbeb',
                  borderColor: '#fde68a',
                  color: '#92400e',
                }}
              >
                Disabling this zone will make it inactive for deliveries and quote calculations. This does not permanently delete the zone or its history.
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
                loadingText="Disabling Zone…"
                disabled={disabling}
                onClick={handleDisableConfirm}
                style={{ backgroundColor: '#dc2626', borderColor: '#dc2626' }}
              >
                Disable Zone
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
