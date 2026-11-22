import { fireEvent, render, screen, waitFor } from '@testing-library/react'
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
    expect(parcelService.getMyParcels).toHaveBeenCalledWith(0, 10, expect.any(AbortSignal), undefined)
  })

  it('asks the server for the chosen status and page', async () => {
    const row = (n, status) => ({
      id: `parcel-${n}`, trackingNumber: `SDPAGE${String(n).padStart(3, '0')}`,
      deliveryAddress: 'Kandy', createdAt: '2026-11-14T08:00:00Z', charge: 450, status,
    })
    parcelService.getMyParcels.mockImplementation((page, _size, _signal, status) =>
      Promise.resolve(status === 'DELIVERED'
        ? { content: [row(page + 50, 'DELIVERED')], totalPages: 3, totalElements: 21 }
        : { content: [row(1, 'CREATED')], totalPages: 1, totalElements: 1 }))
    render(<MemoryRouter><DeliveryHistoryPage /></MemoryRouter>)
    await screen.findByText('SDPAGE001')

    fireEvent.change(screen.getByLabelText('Filter by status'), { target: { value: 'DELIVERED' } })
    expect(await screen.findByText('SDPAGE050')).toBeInTheDocument()
    expect(parcelService.getMyParcels)
      .toHaveBeenLastCalledWith(0, 10, expect.any(AbortSignal), 'DELIVERED')
    expect(screen.getByText('Page 1 of 3')).toBeInTheDocument()
    expect(screen.getByText(/of 21/)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Next page' }))
    expect(await screen.findByText('SDPAGE051')).toBeInTheDocument()
    expect(parcelService.getMyParcels)
      .toHaveBeenLastCalledWith(1, 10, expect.any(AbortSignal), 'DELIVERED')
  })

  it('offers Cancel only on CREATED parcels and Receipt only on DELIVERED ones', async () => {
    const base = { deliveryAddress: 'Kandy', createdAt: '2026-11-14T08:00:00Z', charge: 450 }
    parcelService.getMyParcels.mockResolvedValue({ totalPages: 1, totalElements: 3, content: [
      { ...base, id: 'p-created', trackingNumber: 'SDCREATED1', status: 'CREATED' },
      { ...base, id: 'p-transit', trackingNumber: 'SDTRANSIT1', status: 'IN_TRANSIT' },
      { ...base, id: 'p-delivered', trackingNumber: 'SDDELIVER1', status: 'DELIVERED' },
    ] })
    parcelService.cancelParcel.mockResolvedValue({ id: 'p-created', status: 'CANCELLED' })
    render(<MemoryRouter><DeliveryHistoryPage /></MemoryRouter>)
    await screen.findByText('SDCREATED1')

    expect(screen.getAllByRole('button', { name: 'Cancel' })).toHaveLength(1)
    expect(screen.getAllByRole('button', { name: 'Receipt' })).toHaveLength(1)

    fireEvent.click(screen.getByRole('button', { name: 'Receipt' }))
    await waitFor(() => expect(parcelService.downloadReceipt).toHaveBeenCalledWith('p-delivered'))

    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    await waitFor(() => expect(parcelService.cancelParcel).toHaveBeenCalledWith('p-created'))
    expect(await screen.findByRole('status')).toHaveTextContent('SDCREATED1 has been cancelled')
    expect(screen.queryByRole('button', { name: 'Cancel' })).not.toBeInTheDocument()
  })

  it('links Create Delivery to the registered customer creation route', async () => {
    parcelService.getMyParcels.mockResolvedValue({ content: [] })
    render(<MemoryRouter><DeliveryHistoryPage /></MemoryRouter>)

    await screen.findByText('No deliveries yet')
    expect(screen.getByRole('link', { name: 'Create Delivery' }))
      .toHaveAttribute('href', '/customer/create-delivery')
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
