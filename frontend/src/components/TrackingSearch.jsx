import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { normalizeTrackingNumber, TRACKING_NUMBER_PATTERN } from '../services/parcelService'
import Button from './Button'

/**
 * Tracking-number search. Validates the format, then opens /track/<number>.
 * `id` must be unique on the page.
 */
export default function TrackingSearch({
  id = 'tracking-number',
  initialValue = '',
  size = 'lg',
  placeholder = 'Enter tracking number, e.g. SD-2026-000123',
  variant,
  label,
  buttonText = 'Track',
  helperText,
}) {
  const navigate = useNavigate()
  const [value, setValue] = useState(initialValue)
  const [error, setError] = useState(null)
  const errorId = `${id}-error`

  function handleSubmit(event) {
    event.preventDefault()
    const trackingNumber = normalizeTrackingNumber(value)
    if (trackingNumber === '') {
      setError('Enter a tracking number.')
      return
    }
    if (!TRACKING_NUMBER_PATTERN.test(trackingNumber)) {
      setError('Enter a valid SwiftDrop tracking number (letters, digits and dashes only).')
      return
    }
    setError(null)
    navigate(`/track/${encodeURIComponent(trackingNumber)}`)
  }

  if (variant === 'card') {
    return (
      <form
        className="tracking-search-card-form"
        onSubmit={handleSubmit}
        noValidate
        role="search"
        aria-label="Track a parcel"
      >
        <label htmlFor={id} className="tracking-search-card-label">
          {label || 'Tracking number'}
        </label>
        <div className="tracking-search-card-row">
          <input
            id={id}
            className="tracking-search-card-input"
            placeholder={placeholder}
            value={value}
            autoComplete="off"
            spellCheck="false"
            aria-invalid={Boolean(error) || undefined}
            aria-describedby={error ? errorId : undefined}
            onChange={(event) => setValue(event.target.value)}
          />
          <button type="submit" className="tracking-search-card-button">
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <circle cx="11" cy="11" r="8" />
              <line x1="21" y1="21" x2="16.65" y2="16.65" />
            </svg>
            <span>{buttonText || 'Track Parcel'}</span>
          </button>
        </div>
        {error ? (
          <p id={errorId} className="form-error" style={{ marginTop: '8px' }}>
            {error}
          </p>
        ) : helperText ? (
          <p className="tracking-search-card-helper">{helperText}</p>
        ) : null}
      </form>
    )
  }

  return (
    <form className={`tracking-search tracking-search-${size}`} onSubmit={handleSubmit} noValidate
      role="search" aria-label="Track a parcel">
      <label htmlFor={id} className="visually-hidden">Tracking number</label>
      <div className="tracking-search-row">
        <span className="tracking-search-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" width={size === 'sm' ? 16 : 18} height={size === 'sm' ? 16 : 18} fill="none" stroke="currentColor"
            strokeWidth="2" strokeLinecap="round"><circle cx="11" cy="11" r="7" /><path d="M20 20l-3.5-3.5" /></svg>
        </span>
        <input
          id={id}
          className="tracking-search-input"
          placeholder={placeholder}
          value={value}
          autoComplete="off"
          spellCheck="false"
          aria-invalid={Boolean(error) || undefined}
          aria-describedby={error ? errorId : undefined}
          onChange={(event) => setValue(event.target.value)}
        />
        <Button type="submit" size={size === 'lg' ? 'lg' : size === 'sm' ? 'sm' : 'md'}>Track</Button>
      </div>
      {error && <p id={errorId} className="form-error">{error}</p>}
    </form>
  )
}
