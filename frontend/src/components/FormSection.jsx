/**
 * Groups related fields under a heading, e.g. "Business details".
 * Children are laid out in a responsive two-column grid; give a FormField
 * className="form-field-full" to span both columns.
 */
export default function FormSection({ title, description, children }) {
  return (
    <fieldset className="form-section">
      <legend className="form-section-title">{title}</legend>
      {description && <p className="form-section-description">{description}</p>}
      <div className="form-grid">{children}</div>
    </fieldset>
  )
}
