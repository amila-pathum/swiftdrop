import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DeliveryDetailsPage from './DeliveryDetailsPage'
import { parcelService } from '../../services/parcelService'

vi.mock('../../services/parcelService', () => ({
  parcelService: {
    getParcelById: vi.fn(),
    getParcelHistory: vi.fn(),
  },
}))

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
  beforeEach(() => vi.resetAllMocks())

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

    expect(await screen.findByRole('heading', { name: 'Delivery details' })).toBeInTheDocument()
    expect(screen.getAllByText('SD100001').length).toBeGreaterThan(0)
    expect(screen.getByText('LKR 487.50')).toBeInTheDocument()
    expect(screen.getByText('Parcel created')).toBeInTheDocument()
    expect(screen.getByText('On the way')).toBeInTheDocument()
    expect(parcelService.getParcelById).toHaveBeenCalledWith('parcel-1', expect.any(AbortSignal))
    expect(parcelService.getParcelHistory).toHaveBeenCalledWith('parcel-1', expect.any(AbortSignal))
  })

  it('does not substitute sample data when an API request fails', async () => {
    parcelService.getParcelById.mockRejectedValue(new Error('Parcel service unavailable'))
    parcelService.getParcelHistory.mockResolvedValue([])

    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent('Parcel service unavailable')
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })
})
