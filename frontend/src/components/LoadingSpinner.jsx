/**
 * Spinning indicator. Pass `label` when the spinner is the only loading
 * feedback; omit it when nearby text already describes the loading state.
 */
export default function LoadingSpinner({ size = 'md', label }) {
  const spinner = <span className={`spinner spinner-${size}`} aria-hidden="true" />

  if (!label) {
    return spinner
  }
  return (
    <span role="status" className="spinner-labelled">
      {spinner}
      <span className="visually-hidden">{label}</span>
    </span>
  )
}
