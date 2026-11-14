import { Link, NavLink } from 'react-router-dom'
import './webhooks.css'

export default function WebhookPageLayout({ children, total }) {
  return <div className="merchant-scope webhook-page">
    <nav className="page-breadcrumb" aria-label="Breadcrumb">
      <Link to="/dashboard">Overview</Link><span aria-hidden="true">›</span><span>Webhooks</span>
    </nav>
    <header className="page-header-row">
      <div>
        <h1 className="page-title">Webhooks</h1>
        <p className="page-subtitle">SwiftDrop notifies your store when a parcel&apos;s status changes.</p>
      </div>
    </header>
    <nav className="webhook-tabs" aria-label="Webhook views">
      <NavLink to="/merchant/webhooks" end>Endpoint settings</NavLink>
      <NavLink to="/merchant/webhook-deliveries">
        Delivery History {Number.isFinite(total) && <span className="webhook-count">{total}</span>}
      </NavLink>
    </nav>
    {children}
  </div>
}
