/** Placeholder for a list or panel with nothing to show yet. */
export default function EmptyState({ title, description, icon, action }) {
  return (
    <div className="empty-state">
      {icon && <div className="empty-state-icon" aria-hidden="true">{icon}</div>}
      <p className="empty-state-title">{title}</p>
      {description && <p className="empty-state-description">{description}</p>}
      {action && <div className="empty-state-action">{action}</div>}
    </div>
  )
}
