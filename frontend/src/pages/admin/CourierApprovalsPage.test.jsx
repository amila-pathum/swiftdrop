import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CourierApprovalsPage from './CourierApprovalsPage'
import { courierApplicationService } from '../../services/courierApplicationService'

vi.mock('../../services/courierApplicationService', () => ({
  courierApplicationService: {
    getAllApplications: vi.fn(),
    approveApplication: vi.fn(),
    rejectApplication: vi.fn(),
  },
}))

const APPLICATIONS = [
  {
    id: 1, userId: 42, name: 'Kamal Silva', phone: '0771234567', vehicleType: 'MOTORCYCLE',
    vehicleRegistrationNumber: 'WP-BCD-1234', preferredArea: 'Colombo', status: 'PENDING',
    reviewNote: null, submittedAt: '2026-11-12T10:00:00Z', reviewedAt: null,
  },
  {
    id: 2, userId: 43, name: 'Nadeesha Perera', phone: '0719876543', vehicleType: 'VAN',
    vehicleRegistrationNumber: 'CP-XY-9876', preferredArea: 'Kandy', status: 'APPROVED',
    reviewNote: null, submittedAt: '2026-11-11T10:00:00Z', reviewedAt: '2026-11-12T09:00:00Z',
  },
  {
    id: 3, userId: 44, name: 'Ruwan Fernando', phone: '0755550000', vehicleType: 'TRUCK',
    vehicleRegistrationNumber: 'SP-LK-0001', preferredArea: 'Galle', status: 'REJECTED',
    reviewNote: 'Licence expired', submittedAt: '2026-11-10T10:00:00Z', reviewedAt: '2026-11-11T09:00:00Z',
  },
]

const rowFor = (name) => screen.getByText(name).closest('tr')
const statValue = (label) => screen.getByText(label).parentElement.querySelector('.stat-value')

describe('CourierApprovalsPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    courierApplicationService.getAllApplications.mockResolvedValue(APPLICATIONS)
  })

  it('lists applications with counts and offers review only on pending ones', async () => {
    render(<CourierApprovalsPage />)
    expect(await screen.findByText('Kamal Silva')).toBeInTheDocument()

    expect(statValue('Pending Applications')).toHaveTextContent('1')
    expect(statValue('Approved Couriers')).toHaveTextContent('1')
    expect(statValue('Rejected Applications')).toHaveTextContent('1')

    expect(within(rowFor('Kamal Silva')).getByRole('button', { name: 'Approve' })).toBeInTheDocument()
    expect(within(rowFor('Kamal Silva')).getByRole('button', { name: 'Reject' })).toBeInTheDocument()
    for (const name of ['Nadeesha Perera', 'Ruwan Fernando']) {
      expect(within(rowFor(name)).queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
      expect(within(rowFor(name)).queryByRole('button', { name: 'Reject' })).not.toBeInTheDocument()
    }
    // There is no way to create a courier account from this page.
    expect(screen.queryByRole('button', { name: /create/i })).not.toBeInTheDocument()
  })

  it('approves a pending application after confirmation', async () => {
    courierApplicationService.approveApplication.mockResolvedValue({
      ...APPLICATIONS[0], status: 'APPROVED', reviewedAt: '2026-11-13T09:00:00Z',
    })
    render(<CourierApprovalsPage />)
    await screen.findByText('Kamal Silva')

    fireEvent.click(within(rowFor('Kamal Silva')).getByRole('button', { name: 'Approve' }))
    expect(courierApplicationService.approveApplication).not.toHaveBeenCalled()
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Approve Application' }))

    await waitFor(() => expect(courierApplicationService.approveApplication).toHaveBeenCalledWith(1))
    expect(await screen.findByRole('status')).toHaveTextContent('Courier "Kamal Silva" has been approved')
    expect(statValue('Pending Applications')).toHaveTextContent('0')
    expect(statValue('Approved Couriers')).toHaveTextContent('2')
    expect(within(rowFor('Kamal Silva')).queryByRole('button', { name: 'Reject' })).not.toBeInTheDocument()
  })

  it('requires a reason before rejecting', async () => {
    courierApplicationService.rejectApplication.mockResolvedValue({
      ...APPLICATIONS[0], status: 'REJECTED', reviewNote: 'Vehicle not verified',
    })
    render(<CourierApprovalsPage />)
    await screen.findByText('Kamal Silva')

    fireEvent.click(within(rowFor('Kamal Silva')).getByRole('button', { name: 'Reject' }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirm Rejection' }))
    expect(await screen.findByText('Rejection reason is required.')).toBeInTheDocument()
    expect(courierApplicationService.rejectApplication).not.toHaveBeenCalled()

    fireEvent.change(screen.getByLabelText('Rejection Reason'), { target: { value: ' Vehicle not verified ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Confirm Rejection' }))

    await waitFor(() => expect(courierApplicationService.rejectApplication)
      .toHaveBeenCalledWith(1, 'Vehicle not verified'))
    expect(await screen.findByRole('status')).toHaveTextContent('Courier "Kamal Silva" has been rejected')
    expect(statValue('Rejected Applications')).toHaveTextContent('2')
  })

  it('shows the server message when approval fails and keeps the application pending', async () => {
    courierApplicationService.approveApplication.mockRejectedValue(
      new Error('Identity service could not complete the request.'))
    render(<CourierApprovalsPage />)
    await screen.findByText('Kamal Silva')

    fireEvent.click(within(rowFor('Kamal Silva')).getByRole('button', { name: 'Approve' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Approve Application' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Identity service could not complete the request.')
    expect(statValue('Pending Applications')).toHaveTextContent('1')
    expect(within(rowFor('Kamal Silva')).getByRole('button', { name: 'Approve' })).toBeInTheDocument()
  })

  it('filters by status and search, and shows the rejection reason in details', async () => {
    render(<CourierApprovalsPage />)
    await screen.findByText('Kamal Silva')

    fireEvent.change(screen.getByLabelText('Filter by status'), { target: { value: 'REJECTED' } })
    expect(screen.queryByText('Kamal Silva')).not.toBeInTheDocument()
    expect(screen.getByText('Ruwan Fernando')).toBeInTheDocument()

    fireEvent.click(within(rowFor('Ruwan Fernando')).getByRole('button', { name: 'View Details' }))
    expect(within(screen.getByRole('dialog')).getByText('Licence expired')).toBeInTheDocument()
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Close' }))

    fireEvent.click(screen.getByRole('button', { name: 'Reset' }))
    fireEvent.change(screen.getByLabelText('Search applications'), { target: { value: 'kandy' } })
    expect(screen.getByText('Nadeesha Perera')).toBeInTheDocument()
    expect(screen.queryByText('Kamal Silva')).not.toBeInTheDocument()
  })

  it('shows a retryable error when the list cannot be loaded', async () => {
    courierApplicationService.getAllApplications.mockRejectedValueOnce(new Error('Courier service unavailable'))
    render(<CourierApprovalsPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Courier service unavailable')
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('Kamal Silva')).toBeInTheDocument()
  })
})
