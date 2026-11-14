import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DeliveryHistoryPage from './DeliveryHistoryPage'
import { parcelService } from '../../services/parcelService'

vi.mock('../../services/parcelService', () => ({
  parcelService: {
    getMyParcels: vi.fn(),
    cancelParcel: vi.fn(),
    downloadReceipt: vi.fn(),
  },
}))

describe('DeliveryHistoryPage', () => {
  beforeEach(() => vi.resetAllMocks())

  it('shows only deliveries returned by the authenticated API', async () => {
    parcelService.getMyParcels.mockResolvedValue({ content: [{
      id: 'parcel-1', trackingNumber: 'SDREAL001', deliveryAddress: 'Kandy',
      receiverName: 'Nimal', createdAt: '2026-11-14T08:00:00Z',
      charge: 450, status: 'CREATED',
    }] })
    render(<MemoryRouter><DeliveryHistoryPage /></MemoryRouter>)

    expect(await screen.findByText('SDREAL001')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'View Details' }))
      .toHaveAttribute('href', '/customer/deliveries/parcel-1')
    expect(parcelService.getMyParcels).toHaveBeenCalledWith(0, 100, expect.any(AbortSignal))
  })

  it('shows a genuine empty state instead of preview parcels', async () => {
    parcelService.getMyParcels.mockResolvedValue({ content: [] })
    render(<MemoryRouter><DeliveryHistoryPage /></MemoryRouter>)
    expect(await screen.findByText('No deliveries yet')).toBeInTheDocument()
  })

  it('shows retryable errors instead of preview parcels', async () => {
    parcelService.getMyParcels.mockRejectedValue(new Error('Parcel service unavailable'))
    render(<MemoryRouter><DeliveryHistoryPage /></MemoryRouter>)
    expect(await screen.findByRole('alert')).toHaveTextContent('Parcel service unavailable')
  })
})
