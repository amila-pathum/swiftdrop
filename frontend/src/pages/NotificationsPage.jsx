import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useNotifications } from '../context/NotificationContext'
import Button from '../components/Button'
import EmptyState from '../components/EmptyState'
import LoadingSpinner from '../components/LoadingSpinner'
import NotificationStatusIcon from '../components/NotificationStatusIcon'
import {
  getStatusCategory,
  formatNotificationTitle,
  formatNotificationDescription,
  formatNotificationDate,
} from '../utils/notificationFormatter'

export default function NotificationsPage() {
  const { notifications, unreadCount, isLoading, markAsRead, markAllAsRead } = useNotifications()
  const [filter, setFilter] = useState('ALL') // 'ALL' or 'UNREAD'

  const isNotificationRead = (n) => Boolean(n.read ?? n.isRead)

  const filtered = filter === 'UNREAD'
    ? notifications.filter((n) => !isNotificationRead(n))
    : notifications

  return (
    <div className="delivery-page-container notifications-page" style={{ width: '100%', maxWidth: '1180px', margin: '0 auto', padding: '0 0 32px' }}>
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <div>
          <h1 style={{ fontSize: '24px', fontWeight: 700, margin: '0 0 4px', color: '#0f172a' }}>
            Notifications
          </h1>
          <p style={{ margin: 0, color: '#64748b', fontSize: '14px' }}>
            Stay updated with your parcel shipments, status changes, and delivery alerts.
          </p>
        </div>
        <Button
          variant="secondary"
          size="sm"
          onClick={markAllAsRead}
          disabled={isLoading || unreadCount === 0}
        >
          Mark all as read
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
            const category = getStatusCategory(notification.title, notification.message, notification.status)
            const title = formatNotificationTitle(notification)
            const description = formatNotificationDescription(notification)
            const formattedDate = formatNotificationDate(notification.createdAt)

            return (
              <article
                key={notification.id}
                className={`card notification-card ${isRead ? 'read' : 'unread'}`}
                style={{
                  padding: '16px 20px',
                  backgroundColor: isRead ? '#ffffff' : '#f8faff',
                  borderRadius: '10px',
                  border: '1px solid #e2e8f0',
                  display: 'flex',
                  gap: '14px',
                  alignItems: 'flex-start',
                  transition: 'background-color 0.18s ease, border-color 0.18s ease',
                }}
              >
                {/* Status Icon Wrapper */}
                <div
                  className={`notification-icon-wrapper ${category}`}
                  style={{
                    width: '36px',
                    height: '36px',
                    minWidth: '36px',
                    borderRadius: '50%',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    marginTop: '2px',
                  }}
                >
                  <NotificationStatusIcon category={category} size={18} />
                </div>

                {/* Content */}
                <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: '6px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: '8px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', minWidth: 0 }}>
                      {!isRead && (
                        <span
                          style={{
                            width: '7px',
                            height: '7px',
                            minWidth: '7px',
                            borderRadius: '50%',
                            backgroundColor: '#2563eb',
                            display: 'inline-block',
                          }}
                          aria-label="Unread"
                        />
                      )}
                      <h2 style={{ fontSize: '15px', fontWeight: 600, margin: 0, color: '#0f172a', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {title}
                      </h2>
                    </div>
                    <time
                      dateTime={notification.createdAt}
                      style={{ fontSize: '12px', color: '#64748b', whiteSpace: 'nowrap' }}
                    >
                      {formattedDate}
                    </time>
                  </div>

                  <p style={{ margin: '2px 0 6px', fontSize: '13.5px', color: '#334155', lineHeight: 1.5, wordBreak: 'break-word' }}>
                    {description}
                  </p>

                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '6px', flexWrap: 'wrap', gap: '8px' }}>
                    {notification.trackingNumber ? (
                      <Link
                        to={`/track/${encodeURIComponent(notification.trackingNumber)}`}
                        className="notification-track-link"
                        title={`Track parcel ${notification.trackingNumber}`}
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          fontSize: '13px',
                          fontWeight: 600,
                          color: '#2563eb',
                          textDecoration: 'none',
                        }}
                      >
                        Track Parcel
                      </Link>
                    ) : <div />}

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
                </div>
              </article>
            )
          })}
        </div>
      )}
    </div>
  )
}
