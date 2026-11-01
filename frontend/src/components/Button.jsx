import LoadingSpinner from './LoadingSpinner'

/**
 * @param {'primary'|'secondary'} [variant='primary']
 * @param {boolean} [loading] Disables the button and shows a spinner.
 * @param {string} [loadingText] Replaces the label while loading, e.g. "Signing in…".
 * @param {boolean} [fullWidth]
 */
export default function Button({
  variant = 'primary',
  loading = false,
  loadingText,
  fullWidth = false,
  type = 'button',
  disabled,
  className,
  children,
  ...props
}) {
  const classes = ['button', `button-${variant}`, fullWidth && 'button-full', className]
    .filter(Boolean)
    .join(' ')

  return (
    <button
      type={type}
      className={classes}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading && <LoadingSpinner size="sm" />}
      <span>{loading && loadingText ? loadingText : children}</span>
    </button>
  )
}
