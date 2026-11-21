import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { useCourierProfile } from '../../../hooks/courier/useCourierProfile'
import { useCourierDeliveries } from '../../../hooks/courier/useCourierDeliveries'
import CourierTodayPage from './CourierTodayPage'

vi.mock('../../../hooks/useAuth', () => ({ useAuth: () => ({ user: { id: 42, name: 'Kasun Fernando' } }) }))
vi.mock('../../../hooks/courier/useCourierProfile', () => ({ useCourierProfile: vi.fn() }))
vi.mock('../../../hooks/courier/useCourierDeliveries', () => ({ useCourierDeliveries: vi.fn() }))

describe('courier Today visibility', () => {
  it('preserves the dashboard when a courier profile or assignments are unavailable', () => {
    useCourierProfile.mockReturnValue({ error: { status: 404 } })
    useCourierDeliveries.mockReturnValue({ data: null, error: new Error('Dispatch unavailable'), reload: vi.fn() })
    render(<MemoryRouter><CourierTodayPage /></MemoryRouter>)
    expect(screen.getByRole('heading', { name: 'Hello, Kasun' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'To do' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Completed today' })).toBeInTheDocument()
    expect(screen.getByLabelText('Active count unavailable')).toHaveTextContent('—')
    expect(screen.getByText(/Your courier profile has not been set up/)).toBeInTheDocument()
    expect(screen.queryByText(/You have/)).not.toBeInTheDocument()
  })
  it('renders assigned cards and accurate counts when data is available', () => {
    useCourierProfile.mockReturnValue({ data: null })
    useCourierDeliveries.mockReturnValue({ data: [{ assignment: { id: 'a', active: true }, parcel: { id: 'p', trackingNumber: 'SD123', status: 'PICKED_UP', deliveryAddress: 'Nugegoda' } }] })
    render(<MemoryRouter><CourierTodayPage /></MemoryRouter>)
    expect(screen.getByRole('link', { name: 'Open delivery SD123' })).toBeInTheDocument()
    expect(screen.getByText(/assigned delivery today/)).toBeInTheDocument()
    expect(screen.queryByLabelText('Active count unavailable')).not.toBeInTheDocument()
  })
})
