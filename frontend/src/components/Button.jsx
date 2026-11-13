import { Link } from 'react-router-dom'
import LoadingSpinner from './LoadingSpinner'

function buttonClasses({ variant, size, fullWidth, className }) {
  return ['button', `button-${variant}`, size !== 'md' && `button-${size}`,
    fullWidth && 'button-full', className]
    .filter(Boolean)
    .join(' ')
}

/**
 * @param {'primary'|'secondary'|'outline'|'ghost'|'inverted'} [variant='primary']
 * @param {'sm'|'md'|'lg'} [size='md']
 * @param {boolean} [loading] Disables the button and shows a spinner.
 * @param {string} [loadingText] Replaces the label while loading, e.g. "Signing in…".
 * @param {boolean} [fullWidth]
 */
export default function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  loadingText,
  fullWidth = false,
  type = 'button',
  disabled,
  className,
  children,
  ...props
}) {
  return (
    <button
      type={type}
      className={buttonClasses({ variant, size, fullWidth, className })}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading && <LoadingSpinner size="sm" />}
      <span>{loading && loadingText ? loadingText : children}</span>
    </button>
  )
}

/** A router link that looks like a Button. Takes the same variant/size props. */
export function ButtonLink({
  variant = 'primary',
  size = 'md',
  fullWidth = false,
  className,
  children,
  ...props
}) {
  return (
    <Link className={buttonClasses({ variant, size, fullWidth, className })} {...props}>
      {children}
    </Link>
  )
}
