import { fireEvent, render, screen, waitFor } from '@testing-library/react'
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

    expect(await screen.findByText('Amal')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
    expect(screen.getByText('Available')).toBeInTheDocument()
    expect(screen.getByText('Offline')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Show active parcels of Amal' }))

    await waitFor(() => expect(dispatchService.getAssignments).toHaveBeenCalledWith(
      { state: 'ACTIVE', courierId: 7, pageSize: 100 }))
    expect(await screen.findByText('SD-0001')).toBeInTheDocument()
    expect(screen.getByText('PICKED UP')).toBeInTheDocument()
  })
})
