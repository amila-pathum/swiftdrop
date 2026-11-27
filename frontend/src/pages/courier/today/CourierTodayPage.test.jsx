import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useCourierProfile } from '../../../hooks/courier/useCourierProfile'
import { useCourierDeliveries } from '../../../hooks/courier/useCourierDeliveries'
import { dayKey } from '../../../utils/courier/dashboard'
import CourierTodayPage from './CourierTodayPage'

vi.mock('../../../hooks/useAuth', () => ({ useAuth: () => ({ user: { id: 42, name: 'Kasun Fernando' } }) }))
vi.mock('../../../hooks/courier/useCourierProfile', () => ({ useCourierProfile: vi.fn() }))
vi.mock('../../../hooks/courier/useCourierDeliveries', () => ({ useCourierDeliveries: vi.fn() }))

const today = dayKey()
const shift = (days) => dayKey(new Date(Date.now() + days * 86400000))
const delivery = (id, status, deliveryDate, extra = {}) => ({
  assignment: { id: `a-${id}`, active: true, deliveryDate },
  parcel: { id, trackingNumber: `SD-${id}`, status, receiverName: `Recipient ${id}`, deliveryAddress: `${id} Main Street`, ...extra },
})

describe('courier dashboard', () => {
  beforeEach(() => vi.clearAllMocks())

  it('keeps its layout when the profile or assignments are unavailable', () => {
    useCourierProfile.mockReturnValue({ error: { status: 404 } })
    useCourierDeliveries.mockReturnValue({ data: null, error: new Error('Dispatch unavailable'), reload: vi.fn() })
    render(<MemoryRouter><CourierTodayPage /></MemoryRouter>)
    expect(screen.getByRole('heading', { name: 'Hello, Kasun' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /Active deliveries/ })).toBeInTheDocument()
    expect(screen.getByLabelText('Active deliveries unavailable')).toHaveTextContent('—')
    expect(screen.getByText(/Your courier profile has not been set up/)).toBeInTheDocument()
  })

  it('loads every assignment and shows active parcels from earlier days, overdue first', () => {
    useCourierProfile.mockReturnValue({ data: null })
    useCourierDeliveries.mockReturnValue({ data: [
      delivery('upcoming', 'CREATED', shift(3)),
      delivery('today', 'PICKED_UP', today),
      delivery('old', 'IN_TRANSIT', shift(-4)),
      delivery('done', 'DELIVERED', shift(-1), { history: [{ status: 'DELIVERED', recordedAt: new Date().toISOString() }] }),
      delivery('failed', 'FAILED', shift(-2)),
    ] })
    render(<MemoryRouter><CourierTodayPage /></MemoryRouter>)
    expect(useCourierDeliveries).toHaveBeenCalledWith('all')

    const rows = within(screen.getByRole('list', { name: 'Active deliveries' })).getAllByRole('listitem')
    expect(rows.map((row) => within(row).getByRole('link', { name: /Open delivery/ }).textContent)).toEqual(['SD-old', 'SD-today', 'SD-upcoming'])
    expect(within(rows[0]).getByText(/Overdue/)).toBeInTheDocument()
    expect(within(rows[1]).getByText('Due today')).toBeInTheDocument()

    expect(screen.getByText('1 delivery overdue')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'SD-old' })).toBeInTheDocument()
    const summary = screen.getByLabelText('Delivery summary')
    expect(within(summary).getByText('Active deliveries').parentElement).toHaveTextContent('3')
    expect(within(summary).getByText('Delivered today').parentElement).toHaveTextContent('1')
    expect(within(summary).getByText('Success rate').parentElement).toHaveTextContent('50%')
    expect(within(screen.getByRole('region', { name: 'Recently completed' })).getByText('SD-done')).toBeInTheDocument()
  })

  it('shows an all-caught-up message when nothing is active', () => {
    useCourierProfile.mockReturnValue({ data: null })
    useCourierDeliveries.mockReturnValue({ data: [] })
    render(<MemoryRouter><CourierTodayPage /></MemoryRouter>)
    expect(screen.getByText("You're all caught up")).toBeInTheDocument()
    expect(screen.queryByText(/overdue$/)).not.toBeInTheDocument()
  })
})
