import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import NotificationsPage from './NotificationsPage'
import * as NotificationContextModule from '../context/NotificationContext'

describe('NotificationsPage', () => {
  it('renders notifications and filters by Unread', () => {
    const markAsReadMock = vi.fn()
    const refreshMock = vi.fn()

    const mockNotifications = [
      {
        id: 'n-1',
        title: 'Order Created',
        message: 'Your parcel was booked.',
        trackingNumber: 'SD-111',
        read: true,
        createdAt: '2026-11-13T10:00:00Z',
      },
      {
        id: 'n-2',
        title: 'In Transit',
        message: 'Your parcel is moving.',
        trackingNumber: 'SD-222',
        read: false,
        createdAt: '2026-11-13T12:00:00Z',
      },
    ]

    vi.spyOn(NotificationContextModule, 'useNotifications').mockReturnValue({
      notifications: mockNotifications,
      unreadCount: 1,
      isLoading: false,
      markAsRead: markAsReadMock,
      refresh: refreshMock,
    })

    render(
      <MemoryRouter>
        <NotificationsPage />
      </MemoryRouter>
    )

    // Heading
    expect(screen.getByRole('heading', { level: 1, name: 'Notifications' })).toBeInTheDocument()

    // Both visible initially in 'All'
    expect(screen.getByText('Order Created')).toBeInTheDocument()
    expect(screen.getByText('In Transit')).toBeInTheDocument()

    // Filter by 'Unread'
    const unreadTab = screen.getByRole('button', { name: /Unread/i })
    fireEvent.click(unreadTab)

    expect(screen.queryByText('Order Created')).not.toBeInTheDocument()
    expect(screen.getByText('In Transit')).toBeInTheDocument()

    // Click 'Mark as read'
    const markBtn = screen.getByText('Mark as read')
    fireEvent.click(markBtn)
    expect(markAsReadMock).toHaveBeenCalledWith('n-2')

    // Click 'Refresh'
    const refreshBtn = screen.getByText('Refresh')
    fireEvent.click(refreshBtn)
    expect(refreshMock).toHaveBeenCalled()
  })

  it('renders empty state when there are no notifications', () => {
    vi.spyOn(NotificationContextModule, 'useNotifications').mockReturnValue({
      notifications: [],
      unreadCount: 0,
      isLoading: false,
      markAsRead: vi.fn(),
      refresh: vi.fn(),
    })

    render(
      <MemoryRouter>
        <NotificationsPage />
      </MemoryRouter>
    )

    expect(screen.getByText('No notifications yet')).toBeInTheDocument()
  })
})

