/**
 * Inline message. Errors and warnings are announced immediately (role="alert");
 * success and info politely (role="status").
 * @param {'error'|'warning'|'success'|'info'} [variant='info']
 * @param {string} [title]
 * @param {() => void} [onDismiss] Shows a "Dismiss" button.
 */
export default function Alert({ variant = 'info', title, onDismiss, className, children }) {
  const role = variant === 'error' || variant === 'warning' ? 'alert' : 'status'
  return (
    <div className={['alert', `alert-${variant}`, className].filter(Boolean).join(' ')} role={role}>
      <div className="alert-body">
        {title && <p className="alert-title">{title}</p>}
        {typeof children === 'string' ? <p>{children}</p> : children}
      </div>
      {onDismiss && (
        <button type="button" className="alert-dismiss" onClick={onDismiss}>
          Dismiss
        </button>
      )}
    </div>
  )
}
