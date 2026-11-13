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

describe('MerchantDashboardPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
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
})

