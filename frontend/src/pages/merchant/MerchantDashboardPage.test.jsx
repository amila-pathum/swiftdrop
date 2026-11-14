import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuth } from '../../context/AuthContext'
import MerchantDashboardPage from './MerchantDashboardPage'
import { merchantService } from '../../services/merchantService'

vi.mock('../../context/AuthContext', () => ({ useAuth: vi.fn() }))
vi.mock('../../services/merchantService', () => ({
  merchantService: {
    getMyMerchant: vi.fn(),
    getMyDashboard: vi.fn(),
  },
}))

const MOCK_MERCHANT = {
  id: 8142,
  businessName: 'UrbanNest Commerce',
  registrationNumber: 'BR-2024-008142',
  contactName: 'Alex Morgan',
  email: 'operations@urbannest.co',
  phone: '+1-555-0199',
  address: 'San Francisco, CA',
  status: 'APPROVED',
  rejectionReason: null,
  createdAt: '2026-11-12T10:00:00Z',
}

const MOCK_DASHBOARD_DATA = {
  totalOrders: 1248,
  pendingOrders: 86,
  inTransitOrders: 214,
  deliveredOrders: 948,
  otherOrders: 0,
  recentOrders: [
    {
      id: 841,
      trackingNumber: 'SD-20261112-0841',
      customerName: 'Olivia Bennett',
      destination: 'Austin, TX',
      status: 'IN_TRANSIT',
      createdAt: '2026-11-12T09:30:00Z',
      statusHistory: [
        { status: 'REGISTERED', description: 'Parcel registered', timestamp: '2026-11-12T09:30:00Z' },
        { status: 'PICKED_UP', description: 'Picked up by courier', timestamp: '2026-11-12T10:15:00Z' },
        { status: 'IN_TRANSIT', description: 'Package in transit to Austin hub', timestamp: '2026-11-12T11:00:00Z' },
      ],
    },
    {
      id: 840,
      trackingNumber: 'SD-20261112-0840',
      customerName: 'James Wilson',
      destination: 'Seattle, WA',
      status: 'PENDING',
      createdAt: '2026-11-12T08:15:00Z',
      statusHistory: [],
    },
  ],
}

describe('MerchantDashboardPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    merchantService.getMyDashboard.mockResolvedValue(null)
    useAuth.mockReturnValue({
      user: { id: 1, name: 'Alex Morgan', role: 'MERCHANT' },
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  function renderPage() {
    return render(
      <MemoryRouter>
        <MerchantDashboardPage />
      </MemoryRouter>,
    )
  }

  it('renders page header and breadcrumbs immediately', () => {
    merchantService.getMyMerchant.mockReturnValue(new Promise(() => {}))
    renderPage()

    expect(screen.getByRole('heading', { level: 1, name: 'Your business, at a glance' })).toBeInTheDocument()
    expect(screen.getByText('Workspace')).toBeInTheDocument()
    expect(screen.getByText('Overview')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Loading merchant overview…')
  })

  it('renders approved merchant profile details and order statistics in unavailable state', async () => {
    merchantService.getMyMerchant.mockResolvedValue(MOCK_MERCHANT)
    renderPage()

    await waitFor(() => {
      expect(screen.getByText('UrbanNest Commerce')).toBeInTheDocument()
    })

    expect(screen.getByText('Approved')).toBeInTheDocument()
    expect(screen.getByText(/MRC-008142/)).toBeInTheDocument()
    expect(screen.getByText(/operations@urbannest\.co/)).toBeInTheDocument()
    expect(screen.getByText(/San Francisco, CA/)).toBeInTheDocument()

    // Order statistics cards
    expect(screen.getByText('Total Orders')).toBeInTheDocument()
    expect(screen.getByText('Pending Orders')).toBeInTheDocument()
    expect(screen.getByText('In Transit Orders')).toBeInTheDocument()
    expect(screen.getByText('Delivered Orders')).toBeInTheDocument()
    expect(screen.getByText('Unavailable (Parcel Service pending)')).toBeInTheDocument()

    // Recent orders empty state
    expect(screen.getByText('Order data currently unavailable')).toBeInTheDocument()

    // Workflow links
    expect(screen.getByRole('link', { name: 'Manage API keys →' })).toHaveAttribute('href', '/merchant/api-keys')
    expect(screen.getByRole('link', { name: 'Webhook settings →' })).toHaveAttribute('href', '/merchant/webhooks')
  })

  it('shows pending review notice when merchant status is PENDING', async () => {
    merchantService.getMyMerchant.mockResolvedValue({
      ...MOCK_MERCHANT,
      status: 'PENDING',
    })
    renderPage()

    await waitFor(() => {
      expect(screen.getByRole('status')).toHaveTextContent('Merchant application pending review')
    })
  })

  it('shows rejection notice and reason when merchant status is REJECTED', async () => {
    merchantService.getMyMerchant.mockResolvedValue({
      ...MOCK_MERCHANT,
      status: 'REJECTED',
      rejectionReason: 'Invalid registration certificate',
    })
    renderPage()

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Reason: Invalid registration certificate')
    })
  })

  it('opens and closes the business profile modal', async () => {
    merchantService.getMyMerchant.mockResolvedValue(MOCK_MERCHANT)
    renderPage()

    await waitFor(() => {
      expect(screen.getByText('UrbanNest Commerce')).toBeInTheDocument()
    })

    fireEvent.click(screen.getByRole('button', { name: 'View business profile' }))

    const modal = screen.getByRole('dialog')
    expect(modal).toBeInTheDocument()
    expect(modal).toHaveTextContent('BR-2024-008142')
    expect(modal).toHaveTextContent('+1-555-0199')

    fireEvent.click(screen.getByRole('button', { name: 'Close' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('handles error state and retries', async () => {
    merchantService.getMyMerchant
      .mockRejectedValueOnce(new Error('Network offline'))
      .mockResolvedValueOnce(MOCK_MERCHANT)
    renderPage()

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Network offline')
    })

    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))

    await waitFor(() => {
      expect(screen.getByText('UrbanNest Commerce')).toBeInTheDocument()
    })
  })

  it('allows selecting date filter range', async () => {
    merchantService.getMyMerchant.mockResolvedValue(MOCK_MERCHANT)
    merchantService.getMyDashboard.mockResolvedValue(null)
    renderPage()

    await waitFor(() => {
      expect(screen.getByText('UrbanNest Commerce')).toBeInTheDocument()
    })

    const filterBtn = screen.getByRole('button', { name: 'Filter date range' })
    fireEvent.click(filterBtn)

    const option30Days = screen.getByRole('button', { name: 'Last 30 days' })
    fireEvent.click(option30Days)

    expect(screen.getByRole('button', { name: 'Filter date range' })).toHaveTextContent('Last 30 days')
  })

  it('renders real order statistics and recent orders table when dashboard data is returned', async () => {
    merchantService.getMyMerchant.mockResolvedValue(MOCK_MERCHANT)
    merchantService.getMyDashboard.mockResolvedValue(MOCK_DASHBOARD_DATA)
    renderPage()

    await waitFor(() => {
      expect(screen.getByText('1,248')).toBeInTheDocument()
    })

    expect(screen.getByText('86')).toBeInTheDocument()
    expect(screen.getByText('214')).toBeInTheDocument()
    expect(screen.getByText('948')).toBeInTheDocument()

    // Table rows
    expect(screen.getByText('SD-20261112-0841')).toBeInTheDocument()
    expect(screen.getByText('Olivia Bennett')).toBeInTheDocument()
    expect(screen.getByText('Austin, TX')).toBeInTheDocument()
    expect(screen.getByText('In transit')).toBeInTheDocument()

    expect(screen.getByText('SD-20261112-0840')).toBeInTheDocument()
    expect(screen.getByText('James Wilson')).toBeInTheDocument()
    expect(screen.getByText('Seattle, WA')).toBeInTheDocument()
  })

  it('opens order status history modal when clicking an order tracking number', async () => {
    merchantService.getMyMerchant.mockResolvedValue(MOCK_MERCHANT)
    merchantService.getMyDashboard.mockResolvedValue(MOCK_DASHBOARD_DATA)
    renderPage()

    await waitFor(() => {
      expect(screen.getByText('SD-20261112-0841')).toBeInTheDocument()
    })

    // Click tracking number button
    fireEvent.click(screen.getByText('SD-20261112-0841'))

    // Modal dialog
    const modal = screen.getByRole('dialog')
    expect(modal).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Order Status History' })).toBeInTheDocument()
    expect(modal).toHaveTextContent('Austin, TX')
    expect(modal).toHaveTextContent('Package in transit to Austin hub')
    expect(modal).toHaveTextContent('Picked up by courier')

    // Close modal
    fireEvent.click(screen.getByRole('button', { name: 'Close' }))
    expect(screen.queryByRole('heading', { name: 'Order Status History' })).not.toBeInTheDocument()
  })
})

