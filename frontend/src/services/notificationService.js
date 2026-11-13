import { callEndpoint, apiClient } from './apiClient'
import { API_BASE_URL } from './config'
import { ENDPOINTS } from './endpoints'
import { getAccessToken } from './tokenStorage'

/**
 * Fetches all notifications for the currently logged-in user.
 * @returns {Promise<Array>} List of notifications.
 */
export async function getNotifications() {
  return callEndpoint(ENDPOINTS.notifications)
}

/**
 * Fetches the count of unread notifications for the currently logged-in user.
 * @returns {Promise<{ count: number }>}
 */
export async function getUnreadCount() {
  return callEndpoint(ENDPOINTS.unreadNotificationsCount)
}

/**
 * Marks a single notification as read by ID.
 * @param {string} id Notification UUID
 * @returns {Promise<object>} Updated notification
 */
export async function markNotificationRead(id) {
  if (!id) {
    throw new Error('Notification ID is required')
  }
  return callEndpoint(ENDPOINTS.markNotificationAsRead, { params: { id } })
}

/**
 * Constructs the SSE URL for the authenticated user's notification stream.
 * @returns {string|null} EventSource URL with access_token query param, or null if no token.
 */
export function getUserNotificationStreamUrl() {
  const token = getAccessToken()
  if (!token) {
    return null
  }
  return `${API_BASE_URL}/api/notifications/stream?access_token=${encodeURIComponent(token)}`
}

/**
 * Constructs the public SSE URL for tracking a parcel live status.
 * @param {string} trackingNumber
 * @returns {string} Public EventSource URL
 */
export function getParcelLiveStatusStreamUrl(trackingNumber) {
  if (!trackingNumber) {
    throw new Error('Tracking number is required')
  }
  return `${API_BASE_URL}/api/notifications/parcels/${encodeURIComponent(trackingNumber)}/stream`
}

