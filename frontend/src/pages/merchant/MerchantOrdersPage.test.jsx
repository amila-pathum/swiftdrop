import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MerchantOrdersPage from './MerchantOrdersPage'
import { parcelService } from '../../services/parcelService'

vi.mock('../../services/parcelService', () => ({
  parcelService: { getMerchantParcels: vi.fn() },
}))

const firstPage = {
  content: [{
    id: 'parcel-1', trackingNumber: 'SDMERCHANT001', receiverName: 'Nimal',
    status: 'IN_TRANSIT', charge: 450, createdAt: '2026-11-14T10:00:00Z',
  }],
  totalElements: 11, totalPages: 2, number: 0, size: 10,
}

describe('MerchantOrdersPage', () => {
  beforeEach(() => vi.resetAllMocks())

  it('loads authenticated merchant orders without sample fallback data', async () => {
    parcelService.getMerchantParcels.mockResolvedValue(firstPage)
    render(<MemoryRouter><MerchantOrdersPage /></MemoryRouter>)
    expect(screen.getByRole('status')).toHaveTextContent('Loading merchant orders')
    expect(await screen.findByText('SDMERCHANT001')).toBeInTheDocument()
    expect(screen.getByText('IN TRANSIT')).toBeInTheDocument()
    expect(screen.getByText('LKR 450.00')).toBeInTheDocument()
    expect(parcelService.getMerchantParcels).toHaveBeenCalledWith(0, 10, expect.any(AbortSignal))
  })

  it('requests the next owned page', async () => {
    parcelService.getMerchantParcels
      .mockResolvedValueOnce(firstPage)
      .mockResolvedValueOnce({ ...firstPage, content: [], number: 1 })
    render(<MemoryRouter><MerchantOrdersPage /></MemoryRouter>)
    fireEvent.click(await screen.findByRole('button', { name: 'Next' }))
    await waitFor(() => expect(parcelService.getMerchantParcels)
      .toHaveBeenLastCalledWith(1, 10, expect.any(AbortSignal)))
  })

  it('shows retryable API errors', async () => {
    parcelService.getMerchantParcels.mockRejectedValue(new Error('Parcel service unavailable'))
    render(<MemoryRouter><MerchantOrdersPage /></MemoryRouter>)
    expect(await screen.findByRole('alert')).toHaveTextContent('Parcel service unavailable')
  })
})
