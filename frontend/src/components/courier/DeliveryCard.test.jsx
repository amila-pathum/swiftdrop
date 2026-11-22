import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import DeliveryCard from './DeliveryCard'
import DeliveryTabs from './DeliveryTabs'
describe('courier delivery components', () => {
  it('links only to the assigned parcel details and identifies reassigned work', () => {
    render(<MemoryRouter><DeliveryCard delivery={{ assignment: { id: 'a', active: false, deliveryDate: '2026-11-14' }, parcel: { id: 'p', trackingNumber: 'SD123', status: 'IN_TRANSIT', deliveryAddress: 'Nugegoda' } }} /></MemoryRouter>)
    expect(screen.getByRole('link', { name: 'Open delivery SD123' })).toHaveAttribute('href', '/courier/deliveries/p')
    expect(screen.getByText('Reassigned')).toBeInTheDocument()
    expect(screen.getByText('Nugegoda')).toBeInTheDocument()
  })
  it('supports arrow navigation on filter tabs', () => {
    const change = vi.fn()
    render(<DeliveryTabs selected="active" onChange={change} counts={{ active: 2, completed: 1, all: 3 }} />)
    fireEvent.keyDown(screen.getByRole('tab', { name: /Active/ }), { key: 'ArrowRight' })
    expect(change).toHaveBeenCalledWith('completed')
    expect(screen.getByRole('tab', { name: /Completed/ })).toHaveFocus()
  })
})
