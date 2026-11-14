import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import NotificationToast from './NotificationToast'
import * as NotificationContextModule from '../context/NotificationContext'

describe('NotificationToast', () => {
  it('renders nothing when toast is null', () => {
    vi.spyOn(NotificationContextModule, 'useNotifications').mockReturnValue({
      toast: null,
      dismissToast: vi.fn(),
    })

    const { container } = render(
      <MemoryRouter>
        <NotificationToast />
      </MemoryRouter>
    )

    expect(container.firstChild).toBeNull()
  })

  it('renders pop-up notification and calls dismissToast when dismissed', () => {
    const dismissMock = vi.fn()
    vi.spyOn(NotificationContextModule, 'useNotifications').mockReturnValue({
      toast: {
        id: 'toast-1',
        title: 'Status Alert',
        message: 'Parcel is out for delivery',
        trackingNumber: 'SD-777',
      },
      dismissToast: dismissMock,
    })

    render(
      <MemoryRouter>
        <NotificationToast />
      </MemoryRouter>
    )

    expect(screen.getByText('Status Alert')).toBeInTheDocument()
    expect(screen.getByText('Parcel is out for delivery')).toBeInTheDocument()
    expect(screen.getByText('SD-777')).toBeInTheDocument()
    expect(screen.getByText(/Track Live/i)).toBeInTheDocument()

    const dismissButton = screen.getByRole('button', { name: /dismiss/i })
    fireEvent.click(dismissButton)
    expect(dismissMock).toHaveBeenCalled()
  })
})

