import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  getNotifications,
  getUnreadCount,
  markNotificationRead,
  getUserNotificationStreamUrl,
  getParcelLiveStatusStreamUrl,
} from './notificationService'
import { API_BASE_URL } from './config'
import { saveSession, clearSession } from './tokenStorage'

const json = (status, body) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })

describe('notificationService', () => {
  let fetchMock

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    saveSession('mock-jwt-token', 900)
  })

  afterEach(() => {
    clearSession()
    vi.unstubAllGlobals()
  })

  it('fetches user notifications with Authorization header', async () => {
    const mockData = [
      { id: '1', title: 'Update', message: 'In transit', read: false },
    ]
    fetchMock.mockResolvedValueOnce(json(200, mockData))

    const result = await getNotifications()
    expect(result).toEqual(mockData)
    expect(fetchMock).toHaveBeenCalledWith(
      `${API_BASE_URL}/api/notifications`,
      expect.objectContaining({
        method: 'GET',
        headers: expect.objectContaining({
          Authorization: 'Bearer mock-jwt-token',
        }),
      })
    )
  })

  it('fetches unread count', async () => {
    fetchMock.mockResolvedValueOnce(json(200, { count: 3 }))

    const result = await getUnreadCount()
    expect(result).toEqual({ count: 3 })
    expect(fetchMock).toHaveBeenCalledWith(
      `${API_BASE_URL}/api/notifications/unread-count`,
      expect.objectContaining({
        method: 'GET',
      })
    )
  })

  it('marks a notification as read with PATCH', async () => {
    fetchMock.mockResolvedValueOnce(json(200, { id: 'uuid-1', read: true }))

    const result = await markNotificationRead('uuid-1')
    expect(result).toEqual({ id: 'uuid-1', read: true })
    expect(fetchMock).toHaveBeenCalledWith(
      `${API_BASE_URL}/api/notifications/uuid-1/read`,
      expect.objectContaining({
        method: 'PATCH',
      })
    )
  })

  it('constructs user notification SSE stream URL with access_token', () => {
    const url = getUserNotificationStreamUrl()
    expect(url).toBe(`${API_BASE_URL}/api/notifications/stream?access_token=mock-jwt-token`)
  })

  it('returns null for user notification stream URL when not logged in', () => {
    clearSession()
    expect(getUserNotificationStreamUrl()).toBeNull()
  })

  it('constructs parcel live status public SSE stream URL', () => {
    const url = getParcelLiveStatusStreamUrl('SD-1234-TEST')
    expect(url).toBe(`${API_BASE_URL}/api/notifications/parcels/SD-1234-TEST/stream`)
  })
})
