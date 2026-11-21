import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuth } from '../../context/AuthContext'
import CustomerDashboardPage from './CustomerDashboardPage'
import { parcelService } from '../../services/parcelService'

vi.mock('../../context/AuthContext', () => ({ useAuth: vi.fn() }))
vi.mock('../../services/parcelService', () => ({
  parcelService: { getMyParcels: vi.fn(), getMyParcelSummary: vi.fn() },
  TRACKING_NUMBER_PATTERN: /^[A-Za-z0-9-]{6,50}$/,
}))

const renderPage = () => render(<MemoryRouter><CustomerDashboardPage /></MemoryRouter>)
const statValue = (label) => screen.getByText(label).parentElement.querySelector('.stat-value')

describe('CustomerDashboardPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    useAuth.mockReturnValue({ user: { name: 'Kasun', role: 'CUSTOMER', status: 'ACTIVE' } })
  })

  it('shows counts from the server summary, not from the few recent parcels', async () => {
    parcelService.getMyParcelSummary.mockResolvedValue({
      total: 240,
      byStatus: {
        CREATED: 12, PICKED_UP: 3, IN_TRANSIT: 4, OUT_FOR_DELIVERY: 1,
        DELIVERED: 200, FAILED: 5, CANCELLED: 15,
      },
    })
    parcelService.getMyParcels.mockResolvedValue({ content: [{
      id: 'parcel-1', trackingNumber: 'SDRECENT01', deliveryAddress: 'Kandy',
      createdAt: '2026-11-14T08:00:00Z', charge: 450, status: 'IN_TRANSIT',
    }] })
    renderPage()

    expect(await screen.findByText('SDRECENT01')).toBeInTheDocument()
    expect(statValue('Total Shipments')).toHaveTextContent('240')
    expect(statValue('Active Deliveries')).toHaveTextContent('8')
    expect(statValue('Delivered')).toHaveTextContent('200')
    expect(statValue('Pending Bookings')).toHaveTextContent('12')
    expect(parcelService.getMyParcels).toHaveBeenCalledWith(0, 5, expect.any(AbortSignal))
  })

  it('links to the new delivery form, tracking and parcel details', async () => {
    parcelService.getMyParcelSummary.mockResolvedValue({ total: 1, byStatus: { CREATED: 1 } })
    parcelService.getMyParcels.mockResolvedValue({ content: [{
      id: 'parcel-9', trackingNumber: 'SDLINKS001', deliveryAddress: 'Galle',
      createdAt: '2026-11-14T08:00:00Z', charge: 300, status: 'CREATED',
    }] })
    const { container } = renderPage()
    await screen.findByText('SDLINKS001')

    const hrefs = [...container.querySelectorAll('a')].map((a) => a.getAttribute('href'))
    expect(hrefs).toContain('/customer/create-delivery')
    expect(hrefs).toContain('/customer/deliveries')
    // Each recent parcel opens its details page; tracking is reached through the search box.
    expect(hrefs).toContain('/customer/deliveries/parcel-9')
    expect(container.querySelector('#dashboard-tracking-search')).not.toBeNull()
  })

  it('shows an error instead of zero counts when loading fails', async () => {
    parcelService.getMyParcelSummary.mockRejectedValue(new Error('Parcel service unavailable'))
    parcelService.getMyParcels.mockResolvedValue({ content: [] })
    renderPage()
    expect(await screen.findByText('Parcel service unavailable')).toBeInTheDocument()
  })
})
