import { useMemo, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import Alert from '../../components/Alert'
import LoadingSpinner from '../../components/LoadingSpinner'
import { useAuth } from '../../context/AuthContext'
import * as authService from '../../services/authService'

// Helper: Customer initials for avatar
function getInitials(name) {
  if (!name) return '?'
  const parts = name.trim().split(/\s+/)
  if (parts.length === 1) return parts[0].charAt(0).toUpperCase()
  return (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase()
}

// Inline SVG Icon components
function LockIcon({ size = 18 }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
      <path d="M7 11V7a5 5 0 0 1 10 0v4" />
    </svg>
  )
}

function KeyIcon({ size = 18 }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M21 2l-2 2m-1.5 1.5L16 7l-1.5-1.5-1.5 1.5L14 8l-2 2m-2-2a6 6 0 1 0 8.49 8.49L21 13.5v-3h-3l-1.5-1.5" />
    </svg>
  )
}

function ShieldCheckIcon({ size = 18 }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
      <path d="M9 12l2 2 4-4" />
    </svg>
  )
}

function ShieldIcon({ size = 16 }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
    </svg>
  )
}

function EyeIcon({ crossed }) {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z" />
      <circle cx="12" cy="12" r="3" />
      {crossed && <path d="M3 3l18 18" />}
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

function WarningCircleIcon({ size = 14 }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="12" cy="12" r="10" />
      <line x1="12" y1="8" x2="12" y2="12" />
      <line x1="12" y1="16" x2="12.01" y2="16" />
    </svg>
  )
}

function CheckCircleMiniIcon({ size = 14 }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
      <polyline points="22 4 12 14.01 9 11.01" />
    </svg>
  )
}

function CircleDotIcon({ size = 14 }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="12" cy="12" r="8" />
    </svg>
  )
}

// Password strength calculation helper
function calculatePasswordStrength(password, currentPassword) {
  if (!password) {
    return {
      score: 0,
      label: 'None',
      color: '#cbd5e1',
      percentage: 0,
      requirements: {
        length: false,
        number: false,
        letter: false,
        special: false,
        notSame: true,
      },
    }
  }

  const lengthOk = password.length >= 8 && password.length <= 72
  const hasNumber = /\d/.test(password)
  const hasLetter = /[a-zA-Z]/.test(password)
  const hasSpecial = /[^a-zA-Z0-9]/.test(password)
  const notSame = !currentPassword || password !== currentPassword

  let score = 0
  if (lengthOk) score += 1
  if (hasNumber) score += 1
  if (hasLetter) score += 1
  if (hasSpecial) score += 1

  let label = 'Very Weak'
  let color = '#ef4444' // red
  let percentage = 25

  if (score === 2) {
    label = 'Weak'
    color = '#f97316' // orange
    percentage = 50
  } else if (score === 3) {
    label = 'Fair'
    color = '#eab308' // yellow
    percentage = 75
  } else if (score === 4) {
    label = 'Strong'
    color = '#16a34a' // green
    percentage = 100
  }

  return {
    score,
    label,
    color,
    percentage,
    requirements: {
      length: lengthOk,
      number: hasNumber,
      letter: hasLetter,
      special: hasSpecial,
      notSame,
    },
  }
}

export default function ChangePasswordPage() {
  const { user } = useAuth()
  const navigate = useNavigate()

  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')

  const [showCurrent, setShowCurrent] = useState(false)
  const [showNew, setShowNew] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)

  const [fieldErrors, setFieldErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)
  const submittingRef = useRef(false)

  const [successMessage, setSuccessMessage] = useState('')
  const [errorMessage, setErrorMessage] = useState(null)

  // Real-time strength calculation
  const strength = useMemo(
    () => calculatePasswordStrength(newPassword, currentPassword),
    [newPassword, currentPassword]
  )

  const validate = () => {
    const errors = {}

    if (!currentPassword) {
      errors.currentPassword = 'Enter your current password.'
    }

    if (!newPassword) {
      errors.newPassword = 'Enter your new password.'
    } else if (newPassword.length < 8) {
      errors.newPassword = 'Password must be at least 8 characters.'
    } else if (newPassword.length > 72) {
      errors.newPassword = 'Password must be 72 characters or fewer.'
    } else if (currentPassword && newPassword === currentPassword) {
      errors.newPassword = 'New password cannot be the same as current password.'
    }

    if (!confirmPassword) {
      errors.confirmPassword = 'Confirm your new password.'
    } else if (newPassword && confirmPassword !== newPassword) {
      errors.confirmPassword = 'New password and confirmation must match.'
    }

    return errors
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (submittingRef.current) return

    setErrorMessage(null)
    setSuccessMessage('')

    const errors = validate()
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) {
      const firstInvalid = Object.keys(errors)[0]
      document.getElementById(`field-${firstInvalid}`)?.focus()
      return
    }

    submittingRef.current = true
    setSubmitting(true)
    try {
      const res = await authService.changePassword({
        currentPassword,
        newPassword,
        confirmPassword,
        confirmNewPassword: confirmPassword,
      })

      setSuccessMessage(res?.message || 'Password changed successfully.')
      // Clear password fields after a successful update:
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      setFieldErrors({})
    } catch (err) {
      setErrorMessage(err.message || 'Failed to update password. Please check your credentials and try again.')
    } finally {
      submittingRef.current = false
      setSubmitting(false)
    }
  }

  const handleCancel = () => {
    setCurrentPassword('')
    setNewPassword('')
    setConfirmPassword('')
    setFieldErrors({})
    setErrorMessage(null)
    setSuccessMessage('')
    navigate(-1)
  }

  return (
    <div className="chpw-page">
      {/* Breadcrumb */}
      <nav className="chpw-breadcrumb" aria-label="Breadcrumb">
        <span className="chpw-breadcrumb-muted">Overview</span>
        <span className="chpw-breadcrumb-sep" aria-hidden="true">&rsaquo;</span>
        <Link to="/profile" className="chpw-breadcrumb-link">My Profile</Link>
        <span className="chpw-breadcrumb-sep" aria-hidden="true">&rsaquo;</span>
        <span className="chpw-breadcrumb-active">Change Password</span>
      </nav>

      {/* Global Alerts */}
      {successMessage && (
        <Alert variant="success" onDismiss={() => setSuccessMessage('')}>
          {successMessage}
        </Alert>
      )}
      {errorMessage && (
        <Alert variant="error" onDismiss={() => setErrorMessage(null)}>
          {errorMessage}
        </Alert>
      )}

      {/* Page Header Card */}
      <section className="chpw-header-card" aria-label="Change Password Header">
        <div className="chpw-header-left">
          <div className="chpw-avatar" aria-hidden="true">
            {getInitials(user?.name)}
          </div>
          <div className="chpw-header-text">
            <div className="chpw-header-title-row">
              <span className="chpw-header-icon-badge" aria-hidden="true">
                <LockIcon size={16} />
              </span>
              <h1 className="chpw-header-title">Change Password</h1>
            </div>
            <p className="chpw-header-subtitle">
              Keep your account secure by updating your password regularly.
            </p>
          </div>
        </div>
        <div className="chpw-header-badges">
          <span className="profile-badge-customer">
            <ShieldIcon />
            {user?.role || 'CUSTOMER'}
          </span>
          <span className="profile-badge-active">
            <span className="profile-status-dot" aria-hidden="true" />
            {user?.status || 'ACTIVE'}
          </span>
        </div>
      </section>

      {/* Form Card */}
      <section className="chpw-form-card" aria-label="Password Update Form">
        {/* Card Header */}
        <div className="chpw-card-header">
          <div className="chpw-card-header-icon">
            <LockIcon size={18} />
          </div>
          <div>
            <h2 className="chpw-card-title">Security Credentials</h2>
            <p className="chpw-card-desc">
              Enter your current password followed by your new password.
            </p>
          </div>
        </div>

        {/* Form Body */}
        <form className="chpw-form-body" onSubmit={handleSubmit} noValidate>

          {/* Current Password */}
          <div className="chpw-field">
            <label htmlFor="field-currentPassword" className="chpw-label">
              <span className="chpw-label-icon" aria-hidden="true">
                <KeyIcon size={15} />
              </span>
              <span>Current Password</span>
              <span className="chpw-required" aria-hidden="true">*</span>
            </label>
            <div className="chpw-input-wrap">
              <span className="chpw-input-prefix" aria-hidden="true">
                <KeyIcon size={17} />
              </span>
              <input
                id="field-currentPassword"
                name="currentPassword"
                type={showCurrent ? 'text' : 'password'}
                className={`chpw-input${fieldErrors.currentPassword ? ' chpw-input-error' : ''}`}
                placeholder="Enter your current password"
                autoComplete="current-password"
                required
                aria-required="true"
                aria-invalid={Boolean(fieldErrors.currentPassword) || undefined}
                aria-describedby={fieldErrors.currentPassword ? 'current-password-error' : undefined}
                value={currentPassword}
                onChange={(e) => {
                  setCurrentPassword(e.target.value)
                  if (fieldErrors.currentPassword) {
                    setFieldErrors((prev) => ({ ...prev, currentPassword: '' }))
                  }
                }}
              />
              <button
                type="button"
                className="chpw-eye-btn"
                aria-label={showCurrent ? 'Hide current password' : 'Show current password'}
                aria-pressed={showCurrent}
                aria-controls="field-currentPassword"
                onClick={() => setShowCurrent((prev) => !prev)}
              >
                <EyeIcon crossed={showCurrent} />
              </button>
            </div>
            {fieldErrors.currentPassword && (
              <p id="current-password-error" className="chpw-field-error" role="alert">
                <WarningCircleIcon />
                <span>{fieldErrors.currentPassword}</span>
              </p>
            )}
          </div>

          <div className="chpw-divider" aria-hidden="true" />

          {/* New Password */}
          <div className="chpw-field">
            <label htmlFor="field-newPassword" className="chpw-label">
              <span className="chpw-label-icon" aria-hidden="true">
                <LockIcon size={15} />
              </span>
              <span>New Password</span>
              <span className="chpw-required" aria-hidden="true">*</span>
            </label>
            <div className="chpw-input-wrap">
              <span className="chpw-input-prefix" aria-hidden="true">
                <LockIcon size={17} />
              </span>
              <input
                id="field-newPassword"
                name="newPassword"
                type={showNew ? 'text' : 'password'}
                className={`chpw-input${fieldErrors.newPassword ? ' chpw-input-error' : ''}`}
                placeholder="Enter a new secure password"
                autoComplete="new-password"
                required
                aria-required="true"
                aria-invalid={Boolean(fieldErrors.newPassword) || undefined}
                aria-describedby={fieldErrors.newPassword ? 'new-password-error' : undefined}
                value={newPassword}
                onChange={(e) => {
                  setNewPassword(e.target.value)
                  if (fieldErrors.newPassword) {
                    setFieldErrors((prev) => ({ ...prev, newPassword: '' }))
                  }
                }}
              />
              <button
                type="button"
                className="chpw-eye-btn"
                aria-label={showNew ? 'Hide new password' : 'Show new password'}
                aria-pressed={showNew}
                aria-controls="field-newPassword"
                onClick={() => setShowNew((prev) => !prev)}
              >
                <EyeIcon crossed={showNew} />
              </button>
            </div>
            {fieldErrors.newPassword && (
              <p id="new-password-error" className="chpw-field-error" role="alert">
                <WarningCircleIcon />
                <span>{fieldErrors.newPassword}</span>
              </p>
            )}

            {/* Password Strength Indicator */}
            {newPassword.length > 0 && (
              <div className="chpw-strength-box" aria-live="polite">
                <div className="chpw-strength-row">
                  <span className="chpw-strength-label">Password Strength</span>
                  <span className="chpw-strength-value" style={{ color: strength.color }}>
                    {strength.label}
                  </span>
                </div>
                <div className="chpw-strength-track" aria-hidden="true">
                  <div
                    className="chpw-strength-fill"
                    style={{
                      width: `${strength.percentage}%`,
                      backgroundColor: strength.color,
                    }}
                  />
                </div>
                <ul className="chpw-req-list">
                  {[
                    { key: 'length',  met: strength.requirements.length,  text: '8 to 72 characters long' },
                    { key: 'number',  met: strength.requirements.number,  text: 'Contains at least one number (0–9)' },
                    { key: 'letter',  met: strength.requirements.letter,  text: 'Contains letters (a–z or A–Z)' },
                    { key: 'notSame', met: strength.requirements.notSame, text: 'Different from current password' },
                  ].map(({ key, met, text }) => (
                    <li key={key} className={`chpw-req-item${met ? ' chpw-req-item-met' : ''}`}>
                      <span className="chpw-req-icon" aria-hidden="true">
                        {met ? <CheckCircleMiniIcon /> : <CircleDotIcon />}
                      </span>
                      <span>{text}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>

          {/* Confirm New Password */}
          <div className="chpw-field">
            <label htmlFor="field-confirmPassword" className="chpw-label">
              <span className="chpw-label-icon" aria-hidden="true">
                <ShieldCheckIcon size={15} />
              </span>
              <span>Confirm New Password</span>
              <span className="chpw-required" aria-hidden="true">*</span>
            </label>
            <div className="chpw-input-wrap">
              <span className="chpw-input-prefix" aria-hidden="true">
                <ShieldCheckIcon size={17} />
              </span>
              <input
                id="field-confirmPassword"
                name="confirmPassword"
                type={showConfirm ? 'text' : 'password'}
                className={`chpw-input${fieldErrors.confirmPassword ? ' chpw-input-error' : ''}`}
                placeholder="Re-enter your new password"
                autoComplete="new-password"
                required
                aria-required="true"
                aria-invalid={Boolean(fieldErrors.confirmPassword) || undefined}
                aria-describedby={fieldErrors.confirmPassword ? 'confirm-password-error' : undefined}
                value={confirmPassword}
                onChange={(e) => {
                  setConfirmPassword(e.target.value)
                  if (fieldErrors.confirmPassword) {
                    setFieldErrors((prev) => ({ ...prev, confirmPassword: '' }))
                  }
                }}
              />
              <button
                type="button"
                className="chpw-eye-btn"
                aria-label={showConfirm ? 'Hide confirmation password' : 'Show confirmation password'}
                aria-pressed={showConfirm}
                aria-controls="field-confirmPassword"
                onClick={() => setShowConfirm((prev) => !prev)}
              >
                <EyeIcon crossed={showConfirm} />
              </button>
            </div>
            {fieldErrors.confirmPassword && (
              <p id="confirm-password-error" className="chpw-field-error" role="alert">
                <WarningCircleIcon />
                <span>{fieldErrors.confirmPassword}</span>
              </p>
            )}
          </div>

          {/* Action Buttons */}
          <div className="chpw-actions">
            <button
              type="button"
              className="chpw-btn-cancel"
              disabled={submitting}
              onClick={handleCancel}
            >
              <CloseIcon />
              <span>Cancel</span>
            </button>
            <button
              type="submit"
              className="chpw-btn-submit"
              disabled={submitting}
              aria-busy={submitting || undefined}
            >
              {submitting ? (
                <>
                  <LoadingSpinner size="sm" />
                  <span>Updating Password…</span>
                </>
              ) : (
                <>
                  <CheckMarkIcon />
                  <span>Update Password</span>
                </>
              )}
            </button>
          </div>
        </form>
      </section>
    </div>
  )
}
