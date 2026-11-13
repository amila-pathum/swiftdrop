import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import MerchantApprovalsPage from './MerchantApprovalsPage'
import { merchantService } from '../../services/merchantService'

vi.mock('../../services/merchantService', () => ({
  merchantService: {
    getAllMerchants: vi.fn(),
    approveMerchant: vi.fn(),
    rejectMerchant: vi.fn(),
  },
}))

const MOCK_MERCHANTS = [
  {
    id: 1,
    businessName: 'Harbor & Pine',
    registrationNumber: 'BR-2026-001284',
    contactName: 'Maya Chen',
    email: 'ops@harborpine.co',
    phone: '+1-555-0101',
    address: 'Seattle, WA',
    status: 'PENDING',
    rejectionReason: null,
    createdAt: '2026-11-12T10:00:00Z',
  },
  {
    id: 2,
    businessName: 'UrbanNest Commerce',
    registrationNumber: 'BR-2024-008142',
    contactName: 'Alex Morgan',
    email: 'operations@urbannest.co',
    phone: '+1-555-0102',
    address: 'San Francisco, CA',
    status: 'APPROVED',
    rejectionReason: null,
    createdAt: '2026-11-11T10:00:00Z',
  },
  {
    id: 3,
    businessName: 'MetroCraft Studio',
    registrationNumber: 'BR-2026-001241',
    contactName: 'Ethan Brooks',
    email: 'team@metrocraft.co',
    phone: '+1-555-0103',
    address: 'Austin, TX',
    status: 'REJECTED',
    rejectionReason: 'Invalid tax documentation',
    createdAt: '2026-11-10T10:00:00Z',
  },
]

describe('MerchantApprovalsPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  function renderPage() {
    return render(
      <MemoryRouter>
        <MerchantApprovalsPage />
      </MemoryRouter>,
    )
  }

  it('renders stats, table of applications and status badges', async () => {
    merchantService.getAllMerchants.mockResolvedValue(MOCK_MERCHANTS)
    renderPage()

    expect(screen.getByRole('heading', { level: 1, name: 'Merchant Applications' })).toBeInTheDocument()

    await waitFor(() => {
      expect(screen.getByText('Harbor & Pine')).toBeInTheDocument()
    })

    // Stat cards counts
    expect(screen.getByText('Pending Applications')).toBeInTheDocument()
    expect(screen.getByText('Approved Merchants')).toBeInTheDocument()
    expect(screen.getByText('Rejected Applications')).toBeInTheDocument()

    // Table rows
    expect(screen.getByText('BR-2026-001284')).toBeInTheDocument()
    expect(screen.getByText('Maya Chen')).toBeInTheDocument()
    expect(screen.getByText('ops@harborpine.co')).toBeInTheDocument()
    expect(screen.getByText('Pending')).toBeInTheDocument()
    expect(screen.getByText('Approved')).toBeInTheDocument()
    expect(screen.getByText('Rejected')).toBeInTheDocument()
  })

  it('filters applications by search query', async () => {
    merchantService.getAllMerchants.mockResolvedValue(MOCK_MERCHANTS)
    renderPage()

    await waitFor(() => {
      expect(screen.getByText('Harbor & Pine')).toBeInTheDocument()
    })

    const searchInput = screen.getByLabelText('Search applications')
    fireEvent.change(searchInput, { target: { value: 'UrbanNest' } })

    expect(screen.getByText('UrbanNest Commerce')).toBeInTheDocument()
    expect(screen.queryByText('Harbor & Pine')).not.toBeInTheDocument()
    expect(screen.queryByText('MetroCraft Studio')).not.toBeInTheDocument()
  })

  it('filters applications by status', async () => {
    merchantService.getAllMerchants.mockResolvedValue(MOCK_MERCHANTS)
    renderPage()

    await waitFor(() => {
      expect(screen.getByText('Harbor & Pine')).toBeInTheDocument()
    })

    const statusSelect = screen.getByLabelText('Filter by status')
    fireEvent.change(statusSelect, { target: { value: 'PENDING' } })

    expect(screen.getByText('Harbor & Pine')).toBeInTheDocument()
    expect(screen.queryByText('UrbanNest Commerce')).not.toBeInTheDocument()
    expect(screen.queryByText('MetroCraft Studio')).not.toBeInTheDocument()
  })

  it('approves a pending merchant application', async () => {
    merchantService.getAllMerchants.mockResolvedValue(MOCK_MERCHANTS)
    merchantService.approveMerchant.mockResolvedValue({
      ...MOCK_MERCHANTS[0],
      status: 'APPROVED',
    })

    renderPage()

    await waitFor(() => {
      expect(screen.getByText('Harbor & Pine')).toBeInTheDocument()
    })

    const approveButtons = screen.getAllByRole('button', { name: 'Approve' })
    fireEvent.click(approveButtons[0])

    const modal = screen.getByRole('dialog')
    expect(modal).toBeInTheDocument()
    expect(modal).toHaveTextContent(/Are you sure you want to approve/i)

    fireEvent.click(screen.getByRole('button', { name: 'Approve Application' }))

    await waitFor(() => {
      expect(merchantService.approveMerchant).toHaveBeenCalledWith(1)
    })

    expect(screen.getByText(/Merchant "Harbor & Pine" has been approved/)).toBeInTheDocument()
  })

  it('rejects a pending merchant application with reason', async () => {
    merchantService.getAllMerchants.mockResolvedValue(MOCK_MERCHANTS)
    merchantService.rejectMerchant.mockResolvedValue({
      ...MOCK_MERCHANTS[0],
      status: 'REJECTED',
      rejectionReason: 'Invalid business address',
    })

    renderPage()

    await waitFor(() => {
      expect(screen.getByText('Harbor & Pine')).toBeInTheDocument()
    })

    const rejectButtons = screen.getAllByRole('button', { name: 'Reject' })
    fireEvent.click(rejectButtons[0])

    const modal = screen.getByRole('dialog')
    expect(modal).toBeInTheDocument()
    expect(modal).toHaveTextContent(/Reject Merchant Application/i)

    const textarea = screen.getByLabelText(/Rejection Reason/)
    fireEvent.change(textarea, { target: { value: 'Invalid business address' } })

    fireEvent.click(screen.getByRole('button', { name: 'Confirm Rejection' }))

    await waitFor(() => {
      expect(merchantService.rejectMerchant).toHaveBeenCalledWith(1, 'Invalid business address')
    })

    expect(screen.getByText(/Merchant "Harbor & Pine" has been rejected/)).toBeInTheDocument()
  })

  it('views full application details in modal', async () => {
    merchantService.getAllMerchants.mockResolvedValue(MOCK_MERCHANTS)
    renderPage()

    await waitFor(() => {
      expect(screen.getByText('Harbor & Pine')).toBeInTheDocument()
    })

    const detailButtons = screen.getAllByRole('button', { name: 'View Details' })
    fireEvent.click(detailButtons[2]) // MetroCraft Studio (Rejected)

    const modal = screen.getByRole('dialog')
    expect(modal).toBeInTheDocument()
    expect(modal).toHaveTextContent('MetroCraft Studio')
    expect(modal).toHaveTextContent('team@metrocraft.co')
    expect(modal).toHaveTextContent('Invalid tax documentation')

    fireEvent.click(screen.getByRole('button', { name: 'Close' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})

