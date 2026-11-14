import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { useCourierDeliveries } from '../../../hooks/courier/useCourierDeliveries'
import CourierDeliveriesPage from './CourierDeliveriesPage'
vi.mock('../../../hooks/courier/useCourierDeliveries', () => ({ useCourierDeliveries: vi.fn() }))
const delivery = (id, status) => ({ assignment: { id, active: status !== 'DELIVERED' }, parcel: { id, trackingNumber: `SD${id}`, status } })
describe('assigned deliveries page', () => {
  it('filters active and completed assignments and preserves correct counts', () => {
    useCourierDeliveries.mockReturnValue({ data: [delivery('1', 'PICKED_UP'), delivery('2', 'DELIVERED')], reload: vi.fn() })
    render(<MemoryRouter><CourierDeliveriesPage /></MemoryRouter>)
    expect(screen.getByRole('link', { name: 'Open delivery SD1' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Open delivery SD2' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('tab', { name: /Completed/ }))
    expect(screen.getByRole('link', { name: 'Open delivery SD2' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Open delivery SD1' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('tab', { name: /All/ }))
    expect(screen.getAllByRole('link', { name: /Open delivery/ })).toHaveLength(2)
  })
  it('shows service errors with retry instead of invented assignments', () => {
    const reload = vi.fn()
    useCourierDeliveries.mockReturnValue({ error: new Error('Dispatch unavailable'), reload })
    render(<MemoryRouter><CourierDeliveriesPage /></MemoryRouter>)
    expect(screen.getByText('Dispatch unavailable')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Open delivery/ })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(reload).toHaveBeenCalledOnce()
  })
})
