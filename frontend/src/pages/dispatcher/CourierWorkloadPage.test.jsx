import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import CourierWorkloadPage from './CourierWorkloadPage'
import { dispatchService } from '../../services/dispatchService'

vi.mock('../../services/dispatchService', () => ({
  dispatchService: { getCouriers: vi.fn(), getAssignments: vi.fn() },
}))

describe('CourierWorkloadPage', () => {
  afterEach(() => vi.resetAllMocks())

  it('shows each courier with active count and availability, and their active parcels', async () => {
    dispatchService.getCouriers.mockResolvedValue([
      { courierId: 7, name: 'Amal', active: true, availability: 'AVAILABLE', available: true, activeParcelCount: 3 },
      { courierId: 9, name: 'Bimal', active: true, availability: 'OFFLINE', available: false, activeParcelCount: 0 },
    ])
    dispatchService.getAssignments.mockResolvedValue({
      content: [{ assignmentId: 'a-1', trackingNumber: 'SD-0001', parcelStatus: 'PICKED_UP',
        assignedAt: '2026-11-14T10:00:00Z' }],
      page: 0, size: 100, totalElements: 1, totalPages: 1,
    })
    render(<CourierWorkloadPage />)

    const cards = within(await screen.findByRole('list', { name: 'Couriers' })).getAllByRole('listitem')
    expect(cards[0]).toHaveTextContent('Amal')
    expect(within(cards[0]).getByText('3')).toBeInTheDocument()
    expect(within(cards[0]).getByText('Available')).toBeInTheDocument()
    expect(within(cards[1]).getByText('Offline')).toBeInTheDocument()
    expect(within(cards[1]).getByRole('button', { name: 'Show active parcels of Bimal' })).toBeDisabled()
    const summary = within(screen.getByLabelText('Workload summary'))
    expect(summary.getByText('Available now').parentElement).toHaveTextContent('1')
    expect(summary.getByText('Busiest courier').parentElement).toHaveTextContent('3Amal')

    fireEvent.click(screen.getByRole('button', { name: 'Show active parcels of Amal' }))

    await waitFor(() => expect(dispatchService.getAssignments).toHaveBeenCalledWith(
      { state: 'ACTIVE', courierId: 7, pageSize: 100 }))
    expect(await screen.findByText('SD-0001')).toBeInTheDocument()
    expect(screen.getByText('Picked up')).toBeInTheDocument()
  })
})
