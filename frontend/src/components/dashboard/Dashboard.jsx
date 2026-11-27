import DashboardIcon from './DashboardIcon'
import './dashboard.css'

/** Page title row shared by every role dashboard. */
export function DashboardHeader({ title, subtitle, actions }) {
  return (
    <header className="db-header">
      <div>
        <h1>{title}</h1>
        {subtitle && <p>{subtitle}</p>}
      </div>
      {actions && <div className="db-header-actions">{actions}</div>}
    </header>
  )
}

/**
 * A headline number. `tone` (success | warning | danger | info) colours the icon,
 * and `highlight` tints the whole card when the number needs attention.
 */
export function KpiCard({ icon, label, value, detail, tone = 'info', highlight = false }) {
  return (
    <div className={`dash-kpi dash-kpi-tone-${tone}${highlight ? ` dash-kpi-${tone === 'danger' ? 'danger' : 'warning'}` : ''}`}>
      <span className="dash-kpi-icon">{typeof icon === 'string' ? <DashboardIcon name={icon} /> : icon}</span>
      <div>
        <span className="dash-kpi-label">{label}</span>
        <strong aria-label={value == null ? `${label} unavailable` : undefined}>{value ?? '—'}</strong>
        {detail && <span className="dash-kpi-detail">{detail}</span>}
      </div>
    </div>
  )
}

export function KpiGrid({ label, children }) {
  return <div className="dash-kpis" aria-label={label}>{children}</div>
}

/** A titled card; `flush` removes body padding for lists and tables. */
export function Panel({ id, title, description, actions, children, flush = false, className = '' }) {
  const headingId = id ? `${id}-title` : undefined
  return (
    <section className={`db-panel ${className}`} aria-labelledby={headingId}>
      {(title || actions) && (
        <header className="db-panel-header">
          <div>
            {title && <h2 id={headingId}>{title}</h2>}
            {description && <p>{description}</p>}
          </div>
          {actions && <div className="db-panel-actions">{actions}</div>}
        </header>
      )}
      <div className={flush ? 'db-panel-flush' : 'db-panel-body'}>{children}</div>
    </section>
  )
}

/** Initials in a coloured circle; the colour is stable per name. */
export function Avatar({ name, size = 36 }) {
  const initials = (name || '?').trim().split(/\s+/).slice(0, 2).map((part) => part[0]?.toUpperCase()).join('') || '?'
  const hue = [...(name || '')].reduce((sum, char) => sum + char.charCodeAt(0), 0) % 6
  return <span className={`db-avatar db-avatar-${hue}`} style={{ width: size, height: size, fontSize: size * 0.38 }} aria-hidden="true">{initials}</span>
}
