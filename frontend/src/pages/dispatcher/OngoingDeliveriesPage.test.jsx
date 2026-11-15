import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import OngoingDeliveriesPage from './OngoingDeliveriesPage'
import { dispatchService } from '../../services/dispatchService'

vi.mock('../../services/dispatchService', () => ({ dispatchService: { getDeliveries: vi.fn() } }))

describe('OngoingDeliveriesPage', () => {
  afterEach(() => vi.resetAllMocks())

  it('lists deliveries and sends the status filter and tracking search', async () => {
    dispatchService.getDeliveries.mockResolvedValue({
      content: [{
        parcelId: 'p-1', trackingNumber: 'SD-0001', status: 'OUT_FOR_DELIVERY', courierId: 7,
        courierName: 'Amal', lastUpdatedAt: '2026-11-14T10:00:00Z',
      }],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    })
    render(<OngoingDeliveriesPage />)

    expect(await screen.findByText('SD-0001')).toBeInTheDocument()
    expect(within(screen.getByRole('table')).getByText('OUT FOR DELIVERY')).toBeInTheDocument()
    expect(screen.getByText('Amal')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Tracking number'), { target: { value: 'SD-00' } })
    fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'IN_TRANSIT' } })
    fireEvent.click(screen.getByRole('button', { name: 'Search' }))

    await waitFor(() => expect(dispatchService.getDeliveries).toHaveBeenLastCalledWith(
      { trackingNumber: 'SD-00', status: 'IN_TRANSIT', page: 0, pageSize: 20 }))
  })

  it('shows an error when deliveries cannot be loaded', async () => {
    dispatchService.getDeliveries.mockRejectedValue(new Error('Dispatch data is temporarily unavailable'))
    render(<OngoingDeliveriesPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Dispatch data is temporarily unavailable')
  })
})
