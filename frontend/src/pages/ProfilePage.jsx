import { useCallback, useEffect, useState } from 'react'
import Alert from '../components/Alert'
import Button, { ButtonLink } from '../components/Button'
import LoadingSpinner from '../components/LoadingSpinner'
import { useAuth } from '../context/AuthContext'
import * as authService from '../services/authService'

// Helper: Format date
function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  return !Number.isNaN(date.getTime())
    ? date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
    : '—'
}

// Helper: Customer initials for avatar
function getInitials(name) {
  if (!name) return '?'
  const parts = name.trim().split(/\s+/)
  if (parts.length === 1) return parts[0].charAt(0).toUpperCase()
  return (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase()
}

// Inline SVG Icon components
function UserIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
      <circle cx="12" cy="7" r="4" />
    </svg>
  )
}

function MailIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z" />
      <polyline points="22,6 12,13 2,6" />
    </svg>
  )
}

function PhoneIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" />
    </svg>
  )
}

function MapPinIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" />
      <circle cx="12" cy="10" r="3" />
    </svg>
  )
}

function ShieldIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
    </svg>
  )
}

function CalendarIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
      <line x1="16" y1="2" x2="16" y2="6" />
      <line x1="8" y1="2" x2="8" y2="6" />
      <line x1="3" y1="10" x2="21" y2="10" />
    </svg>
  )
}

function CheckCircleIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
      <polyline points="22 4 12 14.01 9 11.01" />
    </svg>
  )
}

function EditPencilIcon() {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
      <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
    </svg>
  )
}

function PackageIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <line x1="16.5" y1="9.4" x2="7.5" y2="4.21" />
      <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" />
      <polyline points="3.27 6.96 12 12.01 20.73 6.96" />
      <line x1="12" y1="22.08" x2="12" y2="12" />
    </svg>
  )
}

function IdBadgeIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="3" y="4" width="18" height="16" rx="2" />
      <line x1="7" y1="8" x2="17" y2="8" />
      <line x1="7" y1="12" x2="13" y2="12" />
    </svg>
  )
}

function CheckMarkIcon({ size = 16 }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <polyline points="20 6 9 17 4 12" />
    </svg>
  )
}

function CloseIcon({ size = 16 }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <line x1="18" y1="6" x2="6" y2="18" />
      <line x1="6" y1="6" x2="18" y2="18" />
    </svg>
  )
}

function LockIcon({ size = 15 }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
      <path d="M7 11V7a5 5 0 0 1 10 0v4" />
    </svg>
  )
}

function WarningCircleIcon({ size = 14 }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="12" cy="12" r="10" />
      <line x1="12" y1="8" x2="12" y2="12" />
      <line x1="12" y1="16" x2="12.01" y2="16" />
    </svg>
  )
}

const PHONE_CHARS = /^\+?[\d\s()-]+$/

function validateProfileForm({ name, phone, address }) {
  const errors = {}

  const trimmedName = (name ?? '').trim()
  if (!trimmedName) {
    errors.name = 'Enter your full name.'
  } else if (trimmedName.length > 100) {
    errors.name = 'Name must be 100 characters or fewer.'
  }

  const trimmedPhone = (phone ?? '').trim()
  if (trimmedPhone) {
    const digits = trimmedPhone.replace(/\D/g, '').length
    if (!PHONE_CHARS.test(trimmedPhone) || digits < 9 || digits > 15) {
      errors.phone = 'Enter a valid phone number, for example 077 123 4567.'
    } else if (trimmedPhone.length > 30) {
      errors.phone = 'Phone number must be 30 characters or fewer.'
    }
  }

  const trimmedAddress = (address ?? '').trim()
  if (trimmedAddress && trimmedAddress.length > 500) {
    errors.address = 'Address must be 500 characters or fewer.'
  }

  return errors
}

export default function ProfilePage() {
  const { user, refreshUser } = useAuth()

  const [profile, setProfile] = useState(user)
  const [loading, setLoading] = useState(true)
  const [fetchError, setFetchError] = useState(null)

  const [isEditing, setIsEditing] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  const [formData, setFormData] = useState({
    name: user?.name || '',
    phone: user?.phone || '',
    address: user?.address || '',
  })
  const [fieldErrors, setFieldErrors] = useState({})

  const [actionSuccess, setActionSuccess] = useState('')
  const [actionError, setActionError] = useState(null)

  const loadProfile = useCallback(async () => {
    setLoading(true)
    setFetchError(null)
    try {
      const data = await authService.getCurrentUser()
      setProfile(data)
      setFormData({
        name: data?.name || '',
        phone: data?.phone || '',
        address: data?.address || '',
      })
    } catch (err) {
      if (user) {
        setProfile(user)
        setFormData({
          name: user.name || '',
          phone: user.phone || '',
          address: user.address || '',
        })
      } else {
        setFetchError(err.message || 'Unable to load profile.')
      }
    } finally {
      setLoading(false)
    }
  }, [user])

  useEffect(() => {
    loadProfile()
  }, [loadProfile])

  const handleStartEdit = () => {
    setFormData({
      name: profile?.name || '',
      phone: profile?.phone || '',
      address: profile?.address || '',
    })
    setFieldErrors({})
    setActionError(null)
    setActionSuccess('')
    setIsEditing(true)
  }

  const handleCancel = () => {
    setFormData({
      name: profile?.name || '',
      phone: profile?.phone || '',
      address: profile?.address || '',
    })
    setFieldErrors({})
    setActionError(null)
    setIsEditing(false)
  }

  const handleSave = async (e) => {
    e.preventDefault()
    setActionError(null)
    setActionSuccess('')

    const errors = validateProfileForm(formData)
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) {
      const firstInvalid = Object.keys(errors)[0]
      document.getElementById(`profile-${firstInvalid}`)?.focus()
      return
    }

    setSubmitting(true)
    try {
      const payload = {
        name: formData.name.trim(),
        phone: formData.phone.trim() || null,
        address: formData.address.trim() || null,
      }
      const updated = await authService.updateProfile(payload)
      setProfile(updated)
      setFormData({
        name: updated?.name || '',
        phone: updated?.phone || '',
        address: updated?.address || '',
      })
      await refreshUser()
      setIsEditing(false)
      setActionSuccess('Profile details have been updated successfully.')
    } catch (err) {
      setActionError(err.message || 'Failed to update profile. Please check the inputs and try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="profile-dashboard">
      {/* Breadcrumb Navigation */}
      <nav className="page-breadcrumb" aria-label="Breadcrumb">
        <span>Overview</span>
        <span aria-hidden="true">›</span>
        {isEditing ? (
          <>
            <button
              type="button"
              className="breadcrumb-link"
              onClick={handleCancel}
              style={{ background: 'none', border: 'none', padding: 0, font: 'inherit', color: 'var(--color-link)', cursor: 'pointer' }}
            >
              My Profile
            </button>
            <span aria-hidden="true">›</span>
            <strong>Edit Profile</strong>
          </>
        ) : (
          <strong>My Profile</strong>
        )}
      </nav>

      {/* Global Notifications */}
      {actionSuccess && (
        <Alert variant="success" onDismiss={() => setActionSuccess('')}>
          {actionSuccess}
        </Alert>
      )}

      {actionError && (
        <Alert variant="error" onDismiss={() => setActionError(null)}>
          {actionError}
        </Alert>
      )}

      {/* Loading State */}
      {loading ? (
        <div className="card status-card status-card-loading" style={{ margin: '40px auto' }}>
          <LoadingSpinner />
          <p role="status">Loading profile details…</p>
        </div>
      ) : fetchError && !profile ? (
        <div className="card status-card" role="alert" style={{ margin: '40px auto' }}>
          <h2 className="card-title">Unable to load profile</h2>
          <p className="card-subtitle">{fetchError}</p>
          <Button onClick={loadProfile}>Try again</Button>
        </div>
      ) : isEditing ? (
        /* ===================================================================
           EDIT PROFILE MODE
           =================================================================== */
        <>
          {/* Edit Profile Header */}
          <section className="profile-edit-header-card" aria-label="Edit Profile Header">
            <div className="profile-edit-header-left">
              <div className="profile-avatar-medium" aria-hidden="true">
                {getInitials(profile?.name)}
              </div>
              <div className="profile-edit-header-text">
                <div className="profile-edit-title-row">
                  <span className="profile-edit-icon-badge" aria-hidden="true">
                    <EditPencilIcon />
                  </span>
                  <h1 className="profile-edit-title">Edit Profile</h1>
                </div>
                <p className="profile-edit-subtitle">
                  Update your personal information and delivery preferences.
                </p>
              </div>
            </div>
            <div className="profile-hero-badges">
              <span className="profile-badge-customer">
                <ShieldIcon />
                {profile?.role || 'CUSTOMER'}
              </span>
              <span className="profile-badge-active">
                <span className="profile-status-dot" aria-hidden="true" />
                {profile?.status || 'ACTIVE'}
              </span>
            </div>
          </section>

          {/* Edit Profile Form Card */}
          <section className="profile-edit-card" aria-label="Edit Profile Form">
            <div className="profile-edit-card-header">
              <div>
                <h2 className="profile-edit-card-title">
                  <UserIcon />
                  <span>Personal Details & Delivery Information</span>
                </h2>
                <p className="profile-edit-card-desc">
                  These details will be used for your account profile and courier delivery coordination.
                </p>
              </div>
            </div>

            <form className="form" onSubmit={handleSave} noValidate>
              <div className="profile-form-grid">
                {/* Full Name */}
                <div className="profile-input-group">
                  <label htmlFor="profile-name" className="profile-input-label">
                    <span className="profile-input-label-icon" aria-hidden="true"><UserIcon /></span>
                    <span>Full Name</span>
                    <span className="profile-field-required" aria-hidden="true">*</span>
                  </label>
                  <div className="profile-input-container">
                    <input
                      id="profile-name"
                      name="name"
                      type="text"
                      className="profile-modern-input"
                      placeholder="e.g. Dilani Fonseka"
                      autoComplete="name"
                      required
                      aria-required="true"
                      aria-invalid={Boolean(fieldErrors.name) || undefined}
                      aria-describedby={fieldErrors.name ? 'profile-name-error' : undefined}
                      value={formData.name}
                      onChange={(e) => {
                        setFormData((prev) => ({ ...prev, name: e.target.value }))
                        if (fieldErrors.name) setFieldErrors((prev) => ({ ...prev, name: '' }))
                      }}
                    />
                  </div>
                  {fieldErrors.name && (
                    <p id="profile-name-error" className="profile-field-error" role="alert">
                      <WarningCircleIcon />
                      <span>{fieldErrors.name}</span>
                    </p>
                  )}
                </div>

                {/* Phone Number */}
                <div className="profile-input-group">
                  <label htmlFor="profile-phone" className="profile-input-label">
                    <span className="profile-input-label-icon" aria-hidden="true"><PhoneIcon /></span>
                    <span>Phone Number</span>
                  </label>
                  <div className="profile-input-container">
                    <input
                      id="profile-phone"
                      name="phone"
                      type="tel"
                      className="profile-modern-input"
                      placeholder="077 123 4567"
                      autoComplete="tel"
                      aria-invalid={Boolean(fieldErrors.phone) || undefined}
                      aria-describedby={fieldErrors.phone ? 'profile-phone-error' : 'profile-phone-hint'}
                      value={formData.phone}
                      onChange={(e) => {
                        setFormData((prev) => ({ ...prev, phone: e.target.value }))
                        if (fieldErrors.phone) setFieldErrors((prev) => ({ ...prev, phone: '' }))
                      }}
                    />
                  </div>
                  {fieldErrors.phone ? (
                    <p id="profile-phone-error" className="profile-field-error" role="alert">
                      <WarningCircleIcon />
                      <span>{fieldErrors.phone}</span>
                    </p>
                  ) : (
                    <p id="profile-phone-hint" className="profile-field-hint">
                      Format: 077 123 4567 or +94 77 123 4567
                    </p>
                  )}
                </div>

                {/* Account Email (Read-only Reference) */}
                <div className="profile-email-readonly-tile">
                  <div className="profile-email-readonly-info">
                    <div className="profile-email-icon" aria-hidden="true">
                      <MailIcon />
                    </div>
                    <div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <span style={{ fontSize: '11px', fontWeight: 600, color: 'var(--color-text-muted)', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                          Account Email
                        </span>
                        <span className="profile-verified-badge">✓ Verified</span>
                      </div>
                      <span style={{ fontSize: '14px', fontWeight: 600, color: 'var(--color-text)' }}>
                        {profile?.email || '—'}
                      </span>
                    </div>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', color: 'var(--color-text-muted)' }}>
                    <LockIcon />
                    <span>Cannot be modified</span>
                  </div>
                </div>

                {/* Address (Full width) */}
                <div className="profile-input-group profile-form-full">
                  <label htmlFor="profile-address" className="profile-input-label">
                    <span className="profile-input-label-icon" aria-hidden="true"><MapPinIcon /></span>
                    <span>Primary Delivery Address</span>
                  </label>
                  <div className="profile-input-container">
                    <textarea
                      id="profile-address"
                      name="address"
                      rows={3}
                      className="profile-modern-textarea"
                      placeholder="Enter your street address, apartment, building or landmark, city..."
                      autoComplete="street-address"
                      aria-invalid={Boolean(fieldErrors.address) || undefined}
                      aria-describedby={fieldErrors.address ? 'profile-address-error' : 'profile-address-hint'}
                      value={formData.address}
                      onChange={(e) => {
                        setFormData((prev) => ({ ...prev, address: e.target.value }))
                        if (fieldErrors.address) setFieldErrors((prev) => ({ ...prev, address: '' }))
                      }}
                    />
                  </div>
                  {fieldErrors.address && (
                    <p id="profile-address-error" className="profile-field-error" role="alert">
                      <WarningCircleIcon />
                      <span>{fieldErrors.address}</span>
                    </p>
                  )}
                </div>
              </div>

              {/* Action Buttons Bar */}
              <div className="profile-edit-actions-bar">
                <button
                  type="button"
                  className="profile-btn-cancel"
                  disabled={submitting}
                  onClick={handleCancel}
                >
                  <CloseIcon />
                  <span>Cancel</span>
                </button>
                <button
                  type="submit"
                  className="profile-btn-save"
                  disabled={submitting}
                  aria-busy={submitting || undefined}
                >
                  {submitting ? (
                    <>
                      <LoadingSpinner size="sm" />
                      <span>Saving Changes…</span>
                    </>
                  ) : (
                    <>
                      <CheckMarkIcon />
                      <span>Save Changes</span>
                    </>
                  )}
                </button>
              </div>
            </form>
          </section>
        </>
      ) : (
        /* ===================================================================
           VIEW PROFILE MODE (Unchanged)
           =================================================================== */
        <>
          {/* Top Hero Banner & Profile Header */}
          <section className="profile-hero" aria-label="Profile summary">
            <div className="profile-hero-banner" />
            <div className="profile-hero-body">
              <div className="profile-hero-user">
                <div className="profile-avatar-large" aria-hidden="true">
                  {getInitials(profile?.name)}
                </div>
                <div className="profile-hero-meta">
                  <h1 className="profile-hero-name">{profile?.name || 'Customer'}</h1>
                  <div className="profile-hero-badges">
                    <span className="profile-badge-customer">
                      <ShieldIcon />
                      {profile?.role || 'CUSTOMER'}
                    </span>
                    <span className="profile-badge-active">
                      <span className="profile-status-dot" aria-hidden="true" />
                      {profile?.status || 'ACTIVE'}
                    </span>
                  </div>
                </div>
              </div>

              <div className="profile-hero-actions">
                <ButtonLink to="/change-password" variant="outline">
                  <LockIcon size={15} />
                  <span>Change Password</span>
                </ButtonLink>
                <Button variant="primary" onClick={handleStartEdit}>
                  <span>Edit Profile</span>
                </Button>
              </div>
            </div>
          </section>

          {/* View Mode Grid */}
          <div className="profile-grid-container">
            {/* Left Column: Personal & Contact Information */}
            <section className="profile-panel" aria-label="Personal and Contact Details">
              <div className="profile-panel-header">
                <div>
                  <h2 className="profile-panel-title">
                    <UserIcon />
                    <span>Personal & Contact Information</span>
                  </h2>
                  <p className="profile-panel-desc">
                    Information used for booking parcel deliveries and courier pickups.
                  </p>
                </div>
              </div>

              <div className="profile-tiles-grid">
                {/* Full Name */}
                <div className="profile-tile">
                  <div className="profile-tile-icon" aria-hidden="true">
                    <UserIcon />
                  </div>
                  <div className="profile-tile-content">
                    <span className="profile-tile-label">Full Name</span>
                    <span className="profile-tile-value">{profile?.name || '—'}</span>
                  </div>
                </div>

                {/* Email */}
                <div className="profile-tile">
                  <div className="profile-tile-icon" aria-hidden="true">
                    <MailIcon />
                  </div>
                  <div className="profile-tile-content">
                    <span className="profile-tile-label">Email Address</span>
                    <span className="profile-tile-value">
                      {profile?.email || '—'}
                      <span className="profile-verified-badge">✓ Verified</span>
                    </span>
                  </div>
                </div>

                {/* Phone */}
                <div className="profile-tile">
                  <div className="profile-tile-icon" aria-hidden="true">
                    <PhoneIcon />
                  </div>
                  <div className="profile-tile-content">
                    <span className="profile-tile-label">Phone Number</span>
                    <span className="profile-tile-value">
                      {profile?.phone || (
                        <span style={{ color: 'var(--color-text-muted)', fontWeight: 400, fontStyle: 'italic' }}>
                          Not provided
                        </span>
                      )}
                    </span>
                  </div>
                </div>

                {/* Account Type */}
                <div className="profile-tile">
                  <div className="profile-tile-icon" aria-hidden="true">
                    <ShieldIcon />
                  </div>
                  <div className="profile-tile-content">
                    <span className="profile-tile-label">Account Type</span>
                    <span className="profile-tile-value">Standard Customer</span>
                  </div>
                </div>

                {/* Delivery Address - spans full width */}
                <div className="profile-tile profile-tile-full">
                  <div className="profile-tile-icon" aria-hidden="true">
                    <MapPinIcon />
                  </div>
                  <div className="profile-tile-content">
                    <span className="profile-tile-label">Primary Delivery Address</span>
                    <span className="profile-tile-value">
                      {profile?.address || (
                        <span style={{ color: 'var(--color-text-muted)', fontWeight: 400, fontStyle: 'italic' }}>
                          No delivery address saved yet. Click "Edit Profile" to set one.
                        </span>
                      )}
                    </span>
                  </div>
                </div>
              </div>
            </section>

            {/* Right Column: Account Security & Deliveries shortcut */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
              <section className="profile-panel" aria-label="Account Overview">
                <div className="profile-panel-header">
                  <div>
                    <h2 className="profile-panel-title">
                      <CheckCircleIcon />
                      <span>Account Overview</span>
                    </h2>
                    <p className="profile-panel-desc">Account status and credentials</p>
                  </div>
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                  {/* User ID */}
                  <div className="profile-tile">
                    <div className="profile-tile-icon profile-tile-icon-navy" aria-hidden="true">
                      <IdBadgeIcon />
                    </div>
                    <div className="profile-tile-content">
                      <span className="profile-tile-label">User ID</span>
                      <code style={{ fontSize: '13px', fontWeight: 600, color: 'var(--color-primary)' }}>
                        #{profile?.id || '—'}
                      </code>
                    </div>
                  </div>

                  {/* Account Status */}
                  <div className="profile-tile">
                    <div className="profile-tile-icon profile-tile-icon-navy" aria-hidden="true">
                      <CheckCircleIcon />
                    </div>
                    <div className="profile-tile-content">
                      <span className="profile-tile-label">Account Status</span>
                      <div style={{ marginTop: '2px' }}>
                        <span className="profile-badge-active">
                          <span className="profile-status-dot" aria-hidden="true" />
                          {profile?.status || 'ACTIVE'}
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Member Since */}
                  <div className="profile-tile">
                    <div className="profile-tile-icon profile-tile-icon-navy" aria-hidden="true">
                      <CalendarIcon />
                    </div>
                    <div className="profile-tile-content">
                      <span className="profile-tile-label">Member Since</span>
                      <span className="profile-tile-value">{formatDate(profile?.createdAt)}</span>
                    </div>
                  </div>
                </div>
              </section>
            </div>
          </div>
        </>
      )}
    </div>
  )
}
