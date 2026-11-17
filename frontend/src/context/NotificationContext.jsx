import { createContext, useContext, useEffect, useRef, useState } from 'react'
import { useAuth } from './AuthContext'
import { apiClient } from '../services/apiClient'
import {
  getNotifications,
  getUnreadCount,
  getUserNotificationStreamUrl,
  markNotificationRead,
} from '../services/notificationService'

const NotificationContext = createContext(null)

const normalizeNotification = (n) => ({
  ...n,
  read: Boolean(n.read ?? n.isRead),
  isRead: Boolean(n.read ?? n.isRead),
})

export function NotificationProvider({ children }) {
  const { user } = useAuth()
  const [notifications, setNotifications] = useState([])
  const [unreadCount, setUnreadCount] = useState(0)
  const [isLoading, setIsLoading] = useState(false)
  const [isConnected, setIsConnected] = useState(false)
  const [toast, setToast] = useState(null)

  const eventSourceRef = useRef(null)
  const reconnectTimeoutRef = useRef(null)
  const toastTimeoutRef = useRef(null)
  const isMountedRef = useRef(true)

  const showToast = (notification) => {
    if (toastTimeoutRef.current) {
      clearTimeout(toastTimeoutRef.current)
    }
    setToast(notification)
    // Auto-dismiss toast after 6 seconds
    toastTimeoutRef.current = setTimeout(() => {
      setToast(null)
    }, 6000)
  }

  const dismissToast = () => {
    if (toastTimeoutRef.current) {
      clearTimeout(toastTimeoutRef.current)
    }
    setToast(null)
  }

  // Loads initial notifications and count
  const loadNotifications = async () => {
    if (!user) return
    setIsLoading(true)
    try {
      const [list, countData] = await Promise.all([
        getNotifications().catch(() => []),
        getUnreadCount().catch(() => null),
      ])
      if (isMountedRef.current) {
        const normalized = Array.isArray(list) ? list.map(normalizeNotification) : []
        setNotifications(normalized)
        const unreadFromList = normalized.filter((n) => !n.read && !n.isRead).length
        const rawServerCount = countData?.unreadCount ?? countData?.count
        // Real unread count: prioritize server count if present, otherwise calculate from loaded notifications list
        const resolved = typeof rawServerCount === 'number' && rawServerCount > 0
          ? rawServerCount
          : unreadFromList
        setUnreadCount(resolved)
      }
    } finally {
      if (isMountedRef.current) {
        setIsLoading(false)
      }
    }
  }

  const handleMarkAsRead = async (id) => {
    if (!id) return
    try {
      await markNotificationRead(id)
      setNotifications((prev) =>
        prev.map((item) => (item.id === id ? { ...item, read: true, isRead: true } : item))
      )
      setUnreadCount((prev) => Math.max(0, prev - 1))
    } catch {
      // Failed to mark as read
    }
  }

  const handleMarkAllAsRead = async () => {
    const unreadItems = notifications.filter((item) => !item.read && !item.isRead)
    if (unreadItems.length === 0) return

    // Optimistically update all notifications to read
    setNotifications((prev) =>
      prev.map((item) => ({ ...item, read: true, isRead: true }))
    )
    setUnreadCount(0)

    try {
      await apiClient.patch('/api/notifications/read-all').catch(async () => {
        await Promise.all(unreadItems.map((n) => markNotificationRead(n.id).catch(() => {})))
      })
    } catch {
      await Promise.all(unreadItems.map((n) => markNotificationRead(n.id).catch(() => {})))
    }
  }

  useEffect(() => {
    isMountedRef.current = true

    if (!user) {
      setNotifications([])
      setUnreadCount(0)
      setIsConnected(false)
      setToast(null)
      if (eventSourceRef.current) {
        eventSourceRef.current.close()
        eventSourceRef.current = null
      }
      return undefined
    }

    loadNotifications()

    let backoffDelay = 3000

    function connectSSE() {
      if (!isMountedRef.current) return
      const streamUrl = getUserNotificationStreamUrl()
      if (!streamUrl) return

      try {
        const es = new EventSource(streamUrl)
        eventSourceRef.current = es

        es.onopen = () => {
          if (!isMountedRef.current) return
          setIsConnected(true)
          backoffDelay = 3000
        }

        // Handle 'notification' events
        const onNotificationReceived = (data) => {
          if (!isMountedRef.current || !data) return
          const normalized = normalizeNotification(data)
          setNotifications((prev) => [normalized, ...prev.filter((n) => n.id !== normalized.id)])
          setUnreadCount((prev) => prev + 1)
          showToast(normalized)
        }

        es.addEventListener('notification', (e) => {
          try {
            const data = JSON.parse(e.data)
            onNotificationReceived(data)
          } catch {
            // Ignore parse errors
          }
        })

        es.onmessage = (e) => {
          try {
            const data = JSON.parse(e.data)
            if (data?.id) {
              onNotificationReceived(data)
            }
          } catch {
            // Heartbeat or ping
          }
        }

        es.onerror = () => {
          if (!isMountedRef.current) return
          setIsConnected(false)
          es.close()

          reconnectTimeoutRef.current = setTimeout(() => {
            backoffDelay = Math.min(backoffDelay * 1.5, 15000)
            connectSSE()
          }, backoffDelay)
        }
      } catch {
        setIsConnected(false)
      }
    }

    connectSSE()

    return () => {
      isMountedRef.current = false
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current)
      }
      if (toastTimeoutRef.current) {
        clearTimeout(toastTimeoutRef.current)
      }
      if (eventSourceRef.current) {
        eventSourceRef.current.close()
        eventSourceRef.current = null
      }
    }
  }, [user])

  const value = {
    notifications,
    unreadCount,
    isLoading,
    isConnected,
    toast,
    dismissToast,
    markAsRead: handleMarkAsRead,
    markAllAsRead: handleMarkAllAsRead,
    refresh: loadNotifications,
  }

  return (
    <NotificationContext.Provider value={value}>
      {children}
    </NotificationContext.Provider>
  )
}

export function useNotifications() {
  const context = useContext(NotificationContext)
  if (!context) {
    return {
      notifications: [],
      unreadCount: 0,
      isLoading: false,
      isConnected: false,
      toast: null,
      dismissToast: () => {},
      markAsRead: () => {},
      markAllAsRead: () => {},
      refresh: () => {},
    }
  }
  return context
}
