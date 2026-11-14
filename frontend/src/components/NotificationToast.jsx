import { useNotifications } from '../context/NotificationContext'
import { Link } from 'react-router-dom'

export default function NotificationToast() {
  const { toast, dismissToast } = useNotifications()

  if (!toast) return null

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
          borderLeft: '4px solid #2563eb',
          padding: '16px',
          display: 'flex',
          flexDirection: 'column',
          gap: '8px',
          backgroundColor: '#ffffff',
          borderRadius: '8px',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                width: '24px',
                height: '24px',
                borderRadius: '50%',
                backgroundColor: '#eff6ff',
                color: '#2563eb',
              }}
            >
              🔔
            </span>
            <strong style={{ fontSize: '14px', color: '#1e293b' }}>
              {toast.title || 'Parcel Status Update'}
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

        <p style={{ margin: 0, fontSize: '13px', color: '#475569', lineHeight: 1.4 }}>
          {toast.message}
        </p>

        {toast.trackingNumber && (
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '4px' }}>
            <span
              className="pill pill-info"
              style={{
                fontSize: '11px',
                padding: '2px 8px',
                borderRadius: '12px',
                backgroundColor: '#f1f5f9',
                color: '#0f172a',
                fontFamily: 'monospace',
              }}
            >
              {toast.trackingNumber}
            </span>
            <Link
              to={`/track/${encodeURIComponent(toast.trackingNumber)}`}
              onClick={dismissToast}
              style={{ fontSize: '12px', color: '#2563eb', textDecoration: 'none', fontWeight: 500 }}
            >
              Track Live &rarr;
            </Link>
          </div>
        )}
      </div>
    </div>
  )
}

