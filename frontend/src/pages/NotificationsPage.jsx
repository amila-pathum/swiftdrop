import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useNotifications } from '../context/NotificationContext'
import Button from '../components/Button'
import EmptyState from '../components/EmptyState'
import LoadingSpinner from '../components/LoadingSpinner'

function formatDateTime(dateString) {
  if (!dateString) return '—'
  const date = new Date(dateString)
  return Number.isNaN(date.getTime())
    ? '—'
    : date.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}

export default function NotificationsPage() {
  const { notifications, unreadCount, isLoading, markAsRead, refresh } = useNotifications()
  const [filter, setFilter] = useState('ALL') // 'ALL' or 'UNREAD'

  const isNotificationRead = (n) => Boolean(n.read ?? n.isRead)

  const filtered = filter === 'UNREAD'
    ? notifications.filter((n) => !isNotificationRead(n))
    : notifications

  return (
    <div className="page-container notifications-page" style={{ maxWidth: '800px', margin: '0 auto', padding: '24px 16px' }}>
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <div>
          <h1 style={{ fontSize: '24px', fontWeight: 700, margin: '0 0 4px', color: '#0f172a' }}>
            Notifications
          </h1>
          <p style={{ margin: 0, color: '#64748b', fontSize: '14px' }}>
            Stay updated with your parcel shipments, status changes, and delivery alerts.
          </p>
        </div>
        <Button variant="secondary" size="sm" onClick={refresh} disabled={isLoading}>
          {isLoading ? 'Refreshing...' : 'Refresh'}
        </Button>
      </header>

      {/* Filter Tabs */}
      <div style={{ display: 'flex', gap: '8px', borderBottom: '1px solid #e2e8f0', marginBottom: '20px' }}>
        <button
          type="button"
          onClick={() => setFilter('ALL')}
          style={{
            padding: '8px 16px',
            border: 'none',
            background: 'none',
            borderBottom: filter === 'ALL' ? '2px solid #2563eb' : '2px solid transparent',
            color: filter === 'ALL' ? '#2563eb' : '#64748b',
            fontWeight: filter === 'ALL' ? 600 : 500,
            cursor: 'pointer',
            fontSize: '14px',
          }}
        >
          All ({notifications.length})
        </button>
        <button
          type="button"
          onClick={() => setFilter('UNREAD')}
          style={{
            padding: '8px 16px',
            border: 'none',
            background: 'none',
            borderBottom: filter === 'UNREAD' ? '2px solid #2563eb' : '2px solid transparent',
            color: filter === 'UNREAD' ? '#2563eb' : '#64748b',
            fontWeight: filter === 'UNREAD' ? 600 : 500,
            cursor: 'pointer',
            fontSize: '14px',
          }}
        >
          Unread ({unreadCount})
        </button>
      </div>

      {isLoading && notifications.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '40px' }}>
          <LoadingSpinner />
        </div>
      ) : filtered.length === 0 ? (
        <div className="card" style={{ padding: '40px 20px', textAlign: 'center' }}>
          <EmptyState
            title={filter === 'UNREAD' ? 'No unread notifications' : 'No notifications yet'}
            description={
              filter === 'UNREAD'
                ? 'All caught up! You have read all notifications.'
                : 'Status updates for your parcels will appear here once events occur.'
            }
          />
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {filtered.map((notification) => {
            const isRead = isNotificationRead(notification)
            return (
              <article
                key={notification.id}
                className="card notification-card"
                style={{
                  padding: '16px 20px',
                  borderLeft: isRead ? '4px solid #cbd5e1' : '4px solid #2563eb',
                  backgroundColor: isRead ? '#ffffff' : '#f8fafc',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '8px',
                  transition: 'border-color 0.2s, background-color 0.2s',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    {!isRead && (
                      <span
                        style={{
                          width: '8px',
                          height: '8px',
                          borderRadius: '50%',
                          backgroundColor: '#2563eb',
                          display: 'inline-block',
                        }}
                        aria-label="Unread"
                      />
                    )}
                    <h2 style={{ fontSize: '15px', fontWeight: 600, margin: 0, color: '#1e293b' }}>
                      {notification.title}
                    </h2>
                  </div>
                  <time style={{ fontSize: '12px', color: '#94a3b8' }}>
                    {formatDateTime(notification.createdAt)}
                  </time>
                </div>

                <p style={{ margin: 0, fontSize: '14px', color: '#475569', lineHeight: 1.5 }}>
                  {notification.message}
                </p>

                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '4px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    {notification.trackingNumber && (
                      <span
                        className="pill pill-info"
                        style={{
                          fontSize: '12px',
                          fontFamily: 'monospace',
                          padding: '2px 8px',
                          borderRadius: '4px',
                          backgroundColor: '#e2e8f0',
                          color: '#1e293b',
                        }}
                      >
                        {notification.trackingNumber}
                      </span>
                    )}
                    {notification.trackingNumber && (
                      <Link
                        to={`/track/${encodeURIComponent(notification.trackingNumber)}`}
                        style={{ fontSize: '13px', color: '#2563eb', textDecoration: 'none', fontWeight: 500 }}
                      >
                        Track Parcel &rarr;
                      </Link>
                    )}
                  </div>

                  {!isRead && (
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => markAsRead(notification.id)}
                    >
                      Mark as read
                    </Button>
                  )}
                </div>
              </article>
            )
          })}
        </div>
      )}
    </div>
  )
}

