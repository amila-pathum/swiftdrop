/**
 * Card used by every authentication screen: title, optional subtitle, content
 * and footer. `wide` for multi-section forms.
 */
export default function AuthCard({ title, subtitle, wide = false, footer, children }) {
  return (
    <div className={`card auth-card${wide ? ' auth-card-wide' : ''}`}>
      <header className="card-header">
        <h1 className="card-title">{title}</h1>
        {subtitle && <p className="card-subtitle">{subtitle}</p>}
      </header>
      {children}
      {footer && <div className="card-footer">{footer}</div>}
    </div>
  )
}
