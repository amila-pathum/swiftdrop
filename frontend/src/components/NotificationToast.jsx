import { useNotifications } from '../context/NotificationContext'
import { Link } from 'react-router-dom'
import NotificationStatusIcon from './NotificationStatusIcon'
import {
  getStatusCategory,
  formatNotificationTitle,
  formatNotificationDescription,
} from '../utils/notificationFormatter'

export default function NotificationToast() {
  const { toast, dismissToast } = useNotifications()

  if (!toast) return null

  const category = getStatusCategory(toast.title, toast.message, toast.status)
  const title = formatNotificationTitle(toast)
  const description = formatNotificationDescription(toast)

  return (
    <div
      className="notification-toast-container"
      role="region"
      aria-label="Notifications"
      style={{
        position: 'fixed',
        bottom: '24px',
        right: '24px',
        zIndex: 9999,
        maxWidth: '380px',
        width: '100%',
        animation: 'slideUp 0.3s ease-out',
      }}
    >
      <div
        className="card notification-toast"
        style={{
          boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.2), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
          padding: '16px',
          display: 'flex',
          flexDirection: 'column',
          gap: '8px',
          backgroundColor: '#ffffff',
          borderRadius: '10px',
          border: '1px solid #e2e8f0',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span
              className={`notification-icon-wrapper ${category}`}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                width: '26px',
                height: '26px',
                minWidth: '26px',
                borderRadius: '50%',
              }}
            >
              <NotificationStatusIcon category={category} size={15} />
            </span>
            <strong style={{ fontSize: '14px', color: '#0f172a' }}>
              {title}
            </strong>
          </div>
          <button
            type="button"
            className="icon-button"
            onClick={dismissToast}
            aria-label="Dismiss notification"
            style={{
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              color: '#64748b',
              fontSize: '16px',
              padding: '0 4px',
            }}
          >
            ✕
          </button>
        </div>

        <p style={{ margin: 0, fontSize: '13px', color: '#475569', lineHeight: 1.45 }}>
          {description}
        </p>

        {toast.trackingNumber && (
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '4px' }}>
            <span
              className="notification-tracking-badge"
              style={{
                fontSize: '11px',
                padding: '2px 8px',
                borderRadius: '4px',
                backgroundColor: '#e2e8f0',
                color: '#1e293b',
                fontFamily: 'ui-monospace, monospace',
                fontWeight: 600,
              }}
            >
              <span aria-hidden="true">📦 </span>
              <span>{toast.trackingNumber}</span>
            </span>
            <Link
              to={`/track/${encodeURIComponent(toast.trackingNumber)}`}
              onClick={dismissToast}
              style={{ fontSize: '12px', color: '#2563eb', textDecoration: 'none', fontWeight: 600 }}
            >
              Track Live &rarr;
            </Link>
          </div>
        )}
      </div>
    </div>
  )
}
