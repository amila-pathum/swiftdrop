import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import NotificationBell from './NotificationBell'
import * as NotificationContextModule from '../context/NotificationContext'

describe('NotificationBell', () => {
  it('renders bell icon without badge when unreadCount is 0', () => {
    vi.spyOn(NotificationContextModule, 'useNotifications').mockReturnValue({
      notifications: [],
      unreadCount: 0,
      markAsRead: vi.fn(),
    })

    render(
      <MemoryRouter>
        <NotificationBell />
      </MemoryRouter>
    )

    const button = screen.getByRole('button', { name: /notifications/i })
    expect(button).toBeInTheDocument()
    expect(screen.queryByText(/^[0-9]+$/)).not.toBeInTheDocument()
  })

  it('displays badge count when unreadCount > 0', () => {
    vi.spyOn(NotificationContextModule, 'useNotifications').mockReturnValue({
      notifications: [
        { id: '1', title: 'Update', message: 'In transit', read: false },
      ],
      unreadCount: 2,
      markAsRead: vi.fn(),
    })

    render(
      <MemoryRouter>
        <NotificationBell />
      </MemoryRouter>
    )

    expect(screen.getByText('2')).toBeInTheDocument()
  })

  it('toggles dropdown and shows notifications when clicked', () => {
    const markAsReadMock = vi.fn()
    vi.spyOn(NotificationContextModule, 'useNotifications').mockReturnValue({
      notifications: [
        {
          id: 'n-1',
          title: 'Parcel Shipped',
          message: 'Your parcel is on its way.',
          trackingNumber: 'SD-100',
          read: false,
          createdAt: new Date().toISOString(),
        },
      ],
      unreadCount: 1,
      markAsRead: markAsReadMock,
    })

    render(
      <MemoryRouter>
        <NotificationBell />
      </MemoryRouter>
    )

    const button = screen.getByRole('button', { name: /notifications/i })
    fireEvent.click(button)

    expect(screen.getByText('Parcel Shipped')).toBeInTheDocument()
    expect(screen.getByText('Your parcel is on its way.')).toBeInTheDocument()
    expect(screen.getByText('SD-100')).toBeInTheDocument()

    // Click "Mark as read"
    const markButton = screen.getByText('Mark as read')
    fireEvent.click(markButton)
    expect(markAsReadMock).toHaveBeenCalledWith('n-1')

    // Has link to full notifications page
    expect(screen.getByText(/View all notifications/i)).toBeInTheDocument()
  })
})

