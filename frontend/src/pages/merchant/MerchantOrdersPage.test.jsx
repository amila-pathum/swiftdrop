import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MerchantOrdersPage from './MerchantOrdersPage'
import { parcelService } from '../../services/parcelService'
import { merchantService } from '../../services/merchantService'

vi.mock('../../services/parcelService', () => ({
  parcelService: {
    getMerchantParcels: vi.fn(),
    getParcelHistory: vi.fn(),
  },
}))

vi.mock('../../services/merchantService', () => ({
  merchantService: {
    getMyMerchant: vi.fn(),
  },
}))

const firstPage = {
  content: [
    {
      id: 'parcel-1',
      trackingNumber: 'SDMERCHANT001',
      merchantOrderReference: 'SF-1027',
      receiverName: 'Nimal',
      receiverPhone: '0771234567',
      pickupAddress: '45 Galle Road, Colombo 03',
      deliveryAddress: '81 Main Street, Kurunegala',
      weight: 1.1,
      size: 'Small',
      parcelType: 'Denim jacket',
      status: 'IN_TRANSIT',
      charge: 450,
      createdAt: '2026-11-14T10:00:00Z',
    },
    {
      id: 'parcel-2',
      trackingNumber: 'SDMERCHANT002',
      merchantOrderReference: 'SF-1028',
      receiverName: 'Kamal',
      receiverPhone: '0719876543',
      pickupAddress: '45 Galle Road, Colombo 03',
      deliveryAddress: '12 Kandy Road, Kandy',
      weight: 0.5,
      size: 'Small',
      parcelType: 'Documents',
      status: 'DELIVERED',
      charge: 350,
      createdAt: '2026-11-12T10:00:00Z',
    },
  ],
  totalElements: 11,
  totalPages: 2,
  number: 0,
  size: 10,
}

describe('MerchantOrdersPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    merchantService.getMyMerchant.mockResolvedValue({
      businessName: 'UrbanNest Commerce',
      phone: '+94 77 880 3311',
      address: '45 Galle Road, Colombo 03',
    })
    parcelService.getParcelHistory.mockResolvedValue([
      {
        id: 'hist-1',
        previousStatus: 'CREATED',
        newStatus: 'IN_TRANSIT',
        changedAt: '2026-11-14T10:30:00Z',
        note: 'Parcel dispatched from hub',
      },
    ])
  })

  it('loads authenticated merchant orders without sample fallback data', async () => {
    parcelService.getMerchantParcels.mockResolvedValue(firstPage)
    render(<MemoryRouter><MerchantOrdersPage /></MemoryRouter>)
    expect(screen.getByRole('status')).toHaveTextContent('Loading merchant orders')
    expect(await screen.findByText('SDMERCHANT001')).toBeInTheDocument()
    expect(screen.getByText('IN TRANSIT')).toBeInTheDocument()
    expect(screen.getByText('LKR 450.00')).toBeInTheDocument()
    expect(screen.getByText('SF-1027')).toBeInTheDocument()
    expect(screen.getByText('Kurunegala')).toBeInTheDocument()
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

  it('filters orders by search term', async () => {
    parcelService.getMerchantParcels.mockResolvedValue(firstPage)
    render(<MemoryRouter><MerchantOrdersPage /></MemoryRouter>)
    expect(await screen.findByText('SDMERCHANT001')).toBeInTheDocument()
    expect(screen.getByText('SDMERCHANT002')).toBeInTheDocument()

    const searchInput = screen.getByLabelText('Search orders')
    fireEvent.change(searchInput, { target: { value: 'SF-1027' } })

    expect(screen.getByText('SDMERCHANT001')).toBeInTheDocument()
    expect(screen.queryByText('SDMERCHANT002')).not.toBeInTheDocument()
  })

  it('filters orders by status select', async () => {
    parcelService.getMerchantParcels.mockResolvedValue(firstPage)
    render(<MemoryRouter><MerchantOrdersPage /></MemoryRouter>)
    expect(await screen.findByText('SDMERCHANT001')).toBeInTheDocument()
    expect(screen.getByText('SDMERCHANT002')).toBeInTheDocument()

    const statusSelect = screen.getByLabelText('Filter by status')
    fireEvent.change(statusSelect, { target: { value: 'DELIVERED' } })

    expect(screen.queryByText('SDMERCHANT001')).not.toBeInTheDocument()
    expect(screen.getByText('SDMERCHANT002')).toBeInTheDocument()
  })

  it('pre-filters orders based on status query parameter from dashboard card link', async () => {
    parcelService.getMerchantParcels.mockResolvedValue(firstPage)
    render(
      <MemoryRouter initialEntries={['/merchant/orders?status=DELIVERED']}>
        <MerchantOrdersPage />
      </MemoryRouter>,
    )
    expect(await screen.findByText('SDMERCHANT002')).toBeInTheDocument()
    expect(screen.queryByText('SDMERCHANT001')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Filter by status')).toHaveValue('DELIVERED')
  })

  it('opens order details view when Details button is clicked and returns back to list', async () => {
    parcelService.getMerchantParcels.mockResolvedValue(firstPage)
    render(<MemoryRouter><MerchantOrdersPage /></MemoryRouter>)
    expect(await screen.findByText('SDMERCHANT001')).toBeInTheDocument()

    const detailsButtons = screen.getAllByRole('button', { name: 'Details' })
    fireEvent.click(detailsButtons[0])

    // In Order Details view
    expect(await screen.findByRole('heading', { level: 1, name: 'SDMERCHANT001' })).toBeInTheDocument()
    expect(screen.getByText('Live updates active')).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 2, name: 'Pickup' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 2, name: 'Receiver' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 2, name: 'Parcel' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 2, name: 'Progress' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 2, name: 'Fee summary' })).toBeInTheDocument()
    expect(screen.getByText('UrbanNest Commerce')).toBeInTheDocument()
    expect(screen.getByText('81 Main Street, Kurunegala')).toBeInTheDocument()

    // Click Back to Orders
    const backBtn = screen.getByRole('button', { name: /Back to Orders/i })
    fireEvent.click(backBtn)

    // Back in table view
    expect(await screen.findByText('Track and manage all your deliveries in one place.')).toBeInTheDocument()
    expect(screen.getByText('SDMERCHANT001')).toBeInTheDocument()
  })

  it('displays Back to Overview and breadcrumb when navigating from overview dashboard', async () => {
    parcelService.getMerchantParcels.mockResolvedValue(firstPage)
    render(
      <MemoryRouter
        initialEntries={[
          {
            pathname: '/merchant/orders',
            search: '?tracking=SDMERCHANT001',
            state: { selectedOrder: firstPage.content[0], from: 'overview' },
          },
        ]}
      >
        <MerchantOrdersPage />
      </MemoryRouter>,
    )

    expect(await screen.findByRole('heading', { level: 1, name: 'SDMERCHANT001' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Back to Overview' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Overview' })).toBeInTheDocument()
  })
})
