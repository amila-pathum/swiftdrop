import Input from './Input'

/**
 * Label + input + optional hint and inline error, wired together for
 * assistive technology. Remaining props (value, onChange, type, …) go to Input.
 */
export default function FormField({ id, label, hint, error, ...inputProps }) {
  const hintId = hint ? `${id}-hint` : null
  const errorId = error ? `${id}-error` : null
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined

  return (
    <div className="form-field">
      <label htmlFor={id} className="form-label">{label}</label>
      <Input id={id} invalid={Boolean(error)} aria-describedby={describedBy} {...inputProps} />
      {hint && <p id={hintId} className="form-hint">{hint}</p>}
      {error && <p id={errorId} className="form-error">{error}</p>}
    </div>
  )
}
