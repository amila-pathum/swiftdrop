import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useNotifications } from '../context/NotificationContext'
import NotificationStatusIcon from './NotificationStatusIcon'
import {
  getStatusCategory,
  formatNotificationTitle,
  formatNotificationDescription,
  formatNotificationDate,
  formatRelativeTime,
} from '../utils/notificationFormatter'
import './NotificationBell.css'

function BellIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      width="20"
      height="20"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
      <path d="M13.73 21a2 2 0 0 1-3.46 0" />
    </svg>
  )
}

export default function NotificationBell() {
  const { notifications, unreadCount, markAsRead } = useNotifications()
  const [isOpen, setIsOpen] = useState(false)
  const containerRef = useRef(null)

  // Close dropdown on click outside or escape key
  useEffect(() => {
    function handleClickOutside(event) {
      if (containerRef.current && !containerRef.current.contains(event.target)) {
        setIsOpen(false)
      }
    }
    function handleKeyDown(event) {
      if (event.key === 'Escape') {
        setIsOpen(false)
      }
    }
    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside)
      document.addEventListener('keydown', handleKeyDown)
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [isOpen])

  const recent = notifications.slice(0, 4)

  return (
    <div
      ref={containerRef}
      className="notification-bell-container"
    >
      <button
        type="button"
        className="icon-button notification-bell-button"
        onClick={() => setIsOpen((prev) => !prev)}
        aria-label={`Notifications (${unreadCount} unread)`}
        aria-expanded={isOpen}
        aria-haspopup="true"
      >
        <BellIcon />
        {unreadCount > 0 && (
          <span
            className="notification-badge"
            aria-hidden="true"
          >
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

      {isOpen && (
        <div
          className="notification-dropdown card"
          role="menu"
          aria-label="Recent notifications"
        >
          {/* Header */}
          <div className="notification-dropdown-header">
            <span className="notification-dropdown-title">
              Notifications
            </span>
            {unreadCount > 0 && (
              <span className="notification-unread-pill">
                {unreadCount} new
              </span>
            )}
          </div>

          {/* Items without scrollbars */}
          <div className="notification-scroll-area">
            {recent.length === 0 ? (
              <div className="notification-empty">
                <BellIcon />
                <span>No notifications yet.</span>
              </div>
            ) : (
              <ul className="notification-list">
                {recent.map((n) => {
                  const isRead = Boolean(n.read ?? n.isRead)
                  const category = getStatusCategory(n.title, n.message, n.status)
                  const title = formatNotificationTitle(n)
                  const description = formatNotificationDescription(n)
                  const relativeTime = formatRelativeTime(n.createdAt)
                  const fullDate = formatNotificationDate(n.createdAt)

                  return (
                    <li
                      key={n.id}
                      className={`notification-item ${isRead ? 'read' : 'unread'}`}
                    >
                      {/* Icon */}
                      <div className={`notification-icon-wrapper ${category}`}>
                        <NotificationStatusIcon category={category} />
                      </div>

                      {/* Content */}
                      <div className="notification-content">
                        <div className="notification-top-row">
                          <div className="notification-title-group">
                            {!isRead && <span className="notification-unread-dot" aria-hidden="true" />}
                            <strong className="notification-title" title={title}>
                              {title}
                            </strong>
                          </div>
                          <span className="notification-time" title={fullDate}>
                            {relativeTime}
                          </span>
                        </div>

                        <p className="notification-message">
                          {description}
                        </p>

                            {!isRead && (
                          <div className="notification-meta-row">
                            <button
                              type="button"
                              onClick={() => markAsRead(n.id)}
                              className="notification-mark-read-btn"
                            >
                              <span aria-hidden="true">✓</span> Mark as read
                            </button>
                        </div>
                        )}
                      </div>
                    </li>
                  )
                })}
              </ul>
            )}
          </div>

          {/* Footer */}
          <div className="notification-dropdown-footer">
            <Link
              to="/notifications"
              onClick={() => setIsOpen(false)}
              className="notification-view-all-link"
            >
              <span>View all notifications</span>
              <span aria-hidden="true">&rarr;</span>
            </Link>
          </div>
        </div>
      )}
    </div>
  )
}
