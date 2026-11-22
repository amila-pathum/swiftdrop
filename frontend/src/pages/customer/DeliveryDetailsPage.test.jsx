import { render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DeliveryDetailsPage from './DeliveryDetailsPage'
import { parcelService } from '../../services/parcelService'
import { useParcelLiveStatus } from '../../hooks/useParcelLiveStatus'

vi.mock('../../services/parcelService', () => ({
  parcelService: {
    getParcelById: vi.fn(),
    getParcelHistory: vi.fn(),
  },
}))

vi.mock('../../hooks/useParcelLiveStatus', () => ({ useParcelLiveStatus: vi.fn() }))

function renderPage() {
  render(
    <MemoryRouter initialEntries={['/customer/deliveries/parcel-1']}>
      <Routes>
        <Route path="/customer/deliveries/:id" element={<DeliveryDetailsPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('DeliveryDetailsPage', () => {
  const parcel = {
    id: 'parcel-1', trackingNumber: 'SD100001', status: 'CREATED',
    receiverName: 'Nimal', receiverPhone: '0771234567',
    pickupAddress: 'Colombo', deliveryAddress: 'Kandy', weight: 2.5,
    size: 'MEDIUM', parcelType: 'STANDARD', charge: 487.5,
    merchantId: null, merchantOrderReference: null,
    createdAt: '2026-11-14T08:00:00Z',
  }
  beforeEach(() => {
    vi.resetAllMocks()
    parcelService.getParcelById.mockResolvedValue(parcel)
    parcelService.getParcelHistory.mockResolvedValue([])
    useParcelLiveStatus.mockReturnValue({ lastEvent: null })
  })

  it('reloads quietly when the live stream reports the courier picked the parcel up', async () => {
    parcelService.getParcelById
      .mockResolvedValueOnce(parcel)
      .mockResolvedValueOnce({ ...parcel, status: 'PICKED_UP' })
    parcelService.getParcelHistory
      .mockResolvedValueOnce([{ id: 'e1', newStatus: 'CREATED', changedAt: '2026-11-14T08:00:00Z', note: 'Parcel created' }])
      .mockResolvedValueOnce([
        { id: 'e1', newStatus: 'CREATED', changedAt: '2026-11-14T08:00:00Z', note: 'Parcel created' },
        { id: 'e2', newStatus: 'PICKED_UP', changedAt: '2026-11-14T09:00:00Z', note: 'Updated by courier' },
      ])
    useParcelLiveStatus.mockImplementation((trackingNumber) => ({
      lastEvent: trackingNumber ? { status: 'PICKED_UP', occurredAt: '2026-11-14T09:00:00Z' } : null,
    }))

    renderPage()

    const history = within(await screen.findByRole('table', { name: 'Delivery status history' }))
    expect(await history.findByText('Updated by courier')).toBeInTheDocument()
    expect(useParcelLiveStatus).toHaveBeenCalledWith('SD100001')
    expect(parcelService.getParcelById).toHaveBeenCalledTimes(2)
  })

  it('loads the owned parcel and its authenticated history', async () => {
    parcelService.getParcelById.mockResolvedValue({
      id: 'parcel-1', trackingNumber: 'SD100001', status: 'IN_TRANSIT',
      receiverName: 'Nimal', receiverPhone: '0771234567',
      pickupAddress: 'Colombo', deliveryAddress: 'Kandy', weight: 2.5,
      size: 'MEDIUM', parcelType: 'DOCUMENT', charge: 487.5,
      createdAt: '2026-11-14T08:00:00Z', updatedAt: '2026-11-14T09:00:00Z',
    })
    parcelService.getParcelHistory.mockResolvedValue([
      { id: 'event-1', newStatus: 'CREATED', changedAt: '2026-11-14T08:00:00Z', note: 'Parcel created' },
      { id: 'event-2', newStatus: 'IN_TRANSIT', changedAt: '2026-11-14T09:00:00Z', note: 'On the way' },
    ])

    renderPage()

    expect(await screen.findByRole('heading', { name: 'SD100001' })).toBeInTheDocument()
    expect(screen.getAllByText('SD100001').length).toBeGreaterThan(0)
    expect(screen.getByText('LKR 487.50')).toBeInTheDocument()
    const history = within(screen.getByRole('table', { name: 'Delivery status history' }))
    expect(history.getByText('Parcel created')).toBeInTheDocument()
    expect(history.getByText('On the way')).toBeInTheDocument()
    expect(parcelService.getParcelById).toHaveBeenCalledWith('parcel-1', expect.any(AbortSignal))
    expect(parcelService.getParcelHistory).toHaveBeenCalledWith('parcel-1', expect.any(AbortSignal))
  })

  it('shows the booked fee and customer source without invented details', async () => {
    renderPage()
    await screen.findByRole('heading', { name: 'SD100001' })
    expect(screen.getByText('LKR 487.50')).toBeInTheDocument()
    expect(screen.getByText('Customer')).toBeInTheDocument()
    expect(screen.getByText('Booked delivery fee')).toBeInTheDocument()
    expect(screen.queryByText(/Base fee|Additional weight|Size surcharge|rate version|Illustrative/)).not.toBeInTheDocument()
    expect(screen.queryByText('Courier')).not.toBeInTheDocument()
    expect(screen.queryByText('Handling note')).not.toBeInTheDocument()
    expect(screen.queryByText('Merchant order reference')).not.toBeInTheDocument()
  })

  it('uses the merchant source and reference returned by the API', async () => {
    parcelService.getParcelById.mockResolvedValue({ ...parcel, merchantId: 51, merchantOrderReference: 'SHOP-100' })
    renderPage()
    await screen.findByRole('heading', { name: 'SD100001' })
    expect(screen.getByText('Merchant')).toBeInTheDocument()
    expect(screen.getByText('SHOP-100')).toBeInTheDocument()
    expect(screen.queryByText('Customer')).not.toBeInTheDocument()
  })

  it.each(['CANCELLED', 'DELIVERED'])('shows %s history chronologically without guessing actors or attempts', async (status) => {
    parcelService.getParcelById.mockResolvedValue({ ...parcel, status })
    parcelService.getParcelHistory.mockResolvedValue([
      { id: 'last', newStatus: status, changedAt: '2026-11-14T10:00:00Z', changedBy: 42, note: 'Final update' },
      { id: 'first', newStatus: 'CREATED', changedAt: '2026-11-14T08:00:00Z', changedBy: 42, note: 'Parcel created' },
    ])
    renderPage()
    const history = await screen.findByRole('table', { name: 'Delivery status history' })
    const rows = within(history).getAllByRole('row')
    expect(rows[0]).toHaveTextContent('Parcel created')
    expect(rows[1]).toHaveTextContent('Final update')
    expect(within(history).queryByText(/Attempt|Customer|Courier/)).not.toBeInTheDocument()
    if (status === 'CANCELLED') {
      expect(screen.getByText('This delivery was cancelled.')).toBeInTheDocument()
    } else {
      expect(within(screen.getByRole('list', { name: 'Delivery progress' })).getByText('Delivered').closest('li'))
        .toHaveAttribute('aria-current', 'step')
    }
  })

  it('uses current status even when the history is incomplete', async () => {
    parcelService.getParcelById.mockResolvedValue({ ...parcel, status: 'IN_TRANSIT' })
    renderPage()
    await screen.findByRole('heading', { name: 'SD100001' })
    const progress = within(screen.getByRole('list', { name: 'Delivery progress' }))
    expect(progress.getByText('In Transit').closest('li')).toHaveAttribute('aria-current', 'step')
    expect(screen.getByText('No status events have been recorded.')).toBeInTheDocument()
  })

  it.each([null, 'invalid'])('handles unavailable fee and booking date (%s) without inventing a zero charge', async (value) => {
    parcelService.getParcelById.mockResolvedValue({ ...parcel, charge: value, createdAt: value })
    renderPage()
    await screen.findByRole('heading', { name: 'SD100001' })
    const fee = screen.getByRole('region', { name: 'Fee summary' })
    expect(fee).toHaveTextContent('—')
    expect(fee).not.toHaveTextContent('LKR 0')
    expect(screen.getByText('Booked —')).toBeInTheDocument()
  })

  it('displays a recorded zero charge as zero', async () => {
    parcelService.getParcelById.mockResolvedValue({ ...parcel, charge: 0 })
    renderPage()
    expect(await screen.findByText('LKR 0.00')).toBeInTheDocument()
  })

  it('does not substitute sample data when an API request fails', async () => {
    parcelService.getParcelById.mockRejectedValue(new Error('Parcel service unavailable'))
    parcelService.getParcelHistory.mockResolvedValue([])

    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent('Parcel service unavailable')
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })
})
