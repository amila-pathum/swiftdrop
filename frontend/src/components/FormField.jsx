import Input from './Input'

function Control({ as, id, invalid, describedBy, options = [], placeholder, ...props }) {
  const shared = {
    id,
    'aria-invalid': invalid || undefined,
    'aria-describedby': describedBy,
  }
  if (as === 'select') {
    return (
      <select className="input select" {...shared} {...props}>
        <option value="">{placeholder ?? 'Select…'}</option>
        {options.map((option) => (
          <option key={option.value} value={option.value}>{option.label}</option>
        ))}
      </select>
    )
  }
  if (as === 'textarea') {
    return <textarea className="input textarea" rows={3} placeholder={placeholder} {...shared} {...props} />
  }
  return (
    <Input id={id} invalid={invalid} aria-describedby={describedBy} placeholder={placeholder}
      {...props} />
  )
}

/**
 * Label + control + optional hint and inline error, wired together for
 * assistive technology. Remaining props (name, value, onChange, type,
 * revealable, …) go to the control.
 *
 * @param {'input'|'select'|'textarea'} [as='input']
 * @param {{value: string, label: string}[]} [options] For `as="select"`.
 * @param {boolean} [required] Shows a required marker and sets aria-required.
 */
export default function FormField({ id, label, hint, error, as = 'input', required, className, ...props }) {
  const hintId = hint ? `${id}-hint` : null
  const errorId = error ? `${id}-error` : null
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined

  return (
    <div className={['form-field', className].filter(Boolean).join(' ')}>
      <label htmlFor={id} className="form-label">
        {label}
        {required && <span className="form-required" aria-hidden="true"> *</span>}
      </label>
      <Control as={as} id={id} invalid={Boolean(error)} describedBy={describedBy}
        aria-required={required || undefined} {...props} />
      {hint && <p id={hintId} className="form-hint">{hint}</p>}
      {error && <p id={errorId} className="form-error">{error}</p>}
    </div>
  )
}
