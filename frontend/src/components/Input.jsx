import { useState } from 'react'

function EyeIcon({ crossed }) {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor"
      strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z" />
      <circle cx="12" cy="12" r="3" />
      {crossed && <path d="M3 3l18 18" />}
    </svg>
  )
}

/**
 * Text input. With `revealable` on a password input, adds an accessible
 * show/hide toggle. `invalid` marks the input for styling and assistive tech.
 */
export default function Input({
  type = 'text',
  invalid = false,
  revealable = false,
  className,
  ...props
}) {
  const [revealed, setRevealed] = useState(false)
  const canReveal = revealable && type === 'password'

  const input = (
    <input
      type={canReveal && revealed ? 'text' : type}
      className={['input', canReveal && 'input-with-toggle', className].filter(Boolean).join(' ')}
      aria-invalid={invalid || undefined}
      {...props}
    />
  )

  if (!canReveal) {
    return input
  }
  return (
    <div className="input-group">
      {input}
      <button
        type="button"
        className="input-toggle"
        aria-label="Show password"
        aria-pressed={revealed}
        aria-controls={props.id}
        onClick={() => setRevealed((value) => !value)}
      >
        <EyeIcon crossed={revealed} />
      </button>
    </div>
  )
}
