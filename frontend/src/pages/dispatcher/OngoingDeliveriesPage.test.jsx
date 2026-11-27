import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import OngoingDeliveriesPage from './OngoingDeliveriesPage'
import { dispatchService } from '../../services/dispatchService'

vi.mock('../../services/dispatchService', () => ({ dispatchService: { getDeliveries: vi.fn(), getParcel: vi.fn(), getCourierUpdates: vi.fn() } }))

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

  it('opens a delivery to show the courier notes, recipient and photos', async () => {
    dispatchService.getDeliveries.mockResolvedValue({
      content: [{ parcelId: 'p-1', trackingNumber: 'SD-0001', status: 'DELIVERED', courierId: 7, courierName: 'Amal', lastUpdatedAt: '2026-11-14T10:00:00Z' }],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    })
    dispatchService.getParcel.mockResolvedValue({
      parcel: { trackingNumber: 'SD-0001', pickupAddress: 'Kandy', deliveryAddress: 'Colombo', status: 'DELIVERED' }, history: [], assignments: [],
    })
    dispatchService.getCourierUpdates.mockResolvedValue([
      { id: 'u1', status: 'PICKED_UP', attemptNo: 1, recordedAt: '2026-11-14T08:00:00Z', courierName: 'Amal', note: 'Sealed box', photoUrl: 'https://bucket/signed-pickup' },
      { id: 'u2', status: 'FAILED', attemptNo: 1, recordedAt: '2026-11-14T09:00:00Z', courierName: 'Amal', failureReason: 'RECIPIENT_ABSENT' },
      { id: 'u3', status: 'DELIVERED', attemptNo: 2, recordedAt: '2026-11-14T10:00:00Z', courierName: 'Amal', recipientName: 'Nimal' },
    ])
    render(<OngoingDeliveriesPage />)
    fireEvent.click(await screen.findByRole('button', { name: 'SD-0001' }))

    const dialog = await screen.findByRole('dialog')
    expect(dispatchService.getParcel).toHaveBeenCalledWith('p-1')
    expect(dispatchService.getCourierUpdates).toHaveBeenCalledWith('p-1')
    expect(await within(dialog).findByText('Sealed box')).toBeInTheDocument()
    expect(within(dialog).getByRole('img', { name: 'Courier photo for picked up' })).toHaveAttribute('src', 'https://bucket/signed-pickup')
    expect(within(dialog).getByText('Reason: Recipient absent')).toBeInTheDocument()
    expect(within(dialog).getByText('Nimal')).toBeInTheDocument()
    expect(within(dialog).getAllByRole('img')).toHaveLength(1)

    fireEvent.keyDown(window, { key: 'Escape' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('shows an error when deliveries cannot be loaded', async () => {
    dispatchService.getDeliveries.mockRejectedValue(new Error('Dispatch data is temporarily unavailable'))
    render(<OngoingDeliveriesPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Dispatch data is temporarily unavailable')
  })
})
