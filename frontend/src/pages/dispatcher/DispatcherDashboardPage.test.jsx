import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import DispatcherDashboardPage from './DispatcherDashboardPage'
import { dispatchService } from '../../services/dispatchService'

vi.mock('../../services/dispatchService', () => ({
  dispatchService: {
    getUnassigned: vi.fn(),
    getAssignments: vi.fn(),
    getCouriers: vi.fn(),
    getParcel: vi.fn(),
    assignCourier: vi.fn(),
    reassignCourier: vi.fn(),
    cancelAssignment: vi.fn(),
  },
}))

const page = (content) => ({ content, page: 0, size: 10, totalElements: content.length, totalPages: 1 })

const COURIERS = [
  { courierId: 7, userId: 70, name: 'Amal', active: true, availability: 'AVAILABLE', available: true, activeParcelCount: 2 },
  { courierId: 9, userId: 90, name: 'Bimal', active: true, availability: 'BUSY', available: false, activeParcelCount: 0 },
  { courierId: 11, userId: 110, name: 'Chamal', active: true, availability: 'AVAILABLE', available: true, activeParcelCount: 0 },
]

const WAITING = {
  parcelId: 'p-1', trackingNumber: 'SD-0001', pickupArea: 'Colombo', dropoffArea: 'Kandy',
  size: 'SMALL', status: 'CREATED', createdAt: '2026-11-14T10:00:00Z',
}

const assignment = (overrides) => ({
  assignmentId: 'a-1', parcelId: 'p-2', trackingNumber: 'SD-0002', courierId: 7, courierName: 'Amal',
  assignedBy: '42', assignedAt: '2026-11-14T11:00:00Z', state: 'ACTIVE', parcelStatus: 'CREATED', ...overrides,
})

describe('DispatcherDashboardPage', () => {
  beforeEach(() => {
    dispatchService.getUnassigned.mockResolvedValue(page([WAITING]))
    dispatchService.getAssignments.mockResolvedValue(page([
      assignment(),
      assignment({ assignmentId: 'a-2', parcelId: 'p-3', trackingNumber: 'SD-0003', parcelStatus: 'PICKED_UP' }),
    ]))
    dispatchService.getCouriers.mockResolvedValue(COURIERS)
  })

  afterEach(() => vi.resetAllMocks())

  it('lists waiting parcels with every column, without a courier workload panel', async () => {
    render(<DispatcherDashboardPage />)

    expect(await screen.findByRole('button', { name: 'SD-0001' })).toBeInTheDocument()
    expect(screen.getByLabelText('Colombo to Kandy')).toHaveTextContent('FromColomboToKandy')
    expect(screen.getAllByRole('columnheader').map((cell) => cell.textContent))
      .toEqual(['Tracking number', 'Route', 'Size', 'Created', 'Actions'])
    expect(dispatchService.getAssignments).toHaveBeenCalledWith(expect.objectContaining({ state: 'ACTIVE' }))
    expect(screen.queryByRole('heading', { name: 'Courier workload' })).not.toBeInTheDocument()
    expect(within(screen.getByLabelText('Dispatch summary')).getByText('Couriers available')).toBeInTheDocument()
  })

  it('assigns a waiting parcel to the chosen courier; unavailable couriers cannot be picked', async () => {
    dispatchService.assignCourier.mockResolvedValue({})
    render(<DispatcherDashboardPage />)

    fireEvent.click(await screen.findByRole('button', { name: 'Assign SD-0001' }))
    const dialog = screen.getByRole('dialog', { name: 'Assign courier' })
    const select = within(dialog).getByLabelText('Courier')
    expect(within(select).getByRole('option', { name: 'Amal · 2 active · Available' })).not.toBeDisabled()
    expect(within(select).getByRole('option', { name: 'Bimal · 0 active · Busy' })).toBeDisabled()

    fireEvent.change(select, { target: { value: '7' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Assign' }))

    await waitFor(() => expect(dispatchService.assignCourier).toHaveBeenCalledWith('p-1', '7'))
    expect(await screen.findByRole('status')).toHaveTextContent('SD-0001 assigned.')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('shows the server message when an assignment is refused', async () => {
    dispatchService.assignCourier.mockRejectedValue(new Error('Parcel already has an active assignment'))
    render(<DispatcherDashboardPage />)

    fireEvent.click(await screen.findByRole('button', { name: 'Assign SD-0001' }))
    const dialog = screen.getByRole('dialog', { name: 'Assign courier' })
    fireEvent.change(within(dialog).getByLabelText('Courier'), { target: { value: '7' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Assign' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Parcel already has an active assignment')
  })

  it('reassigns and cancels active assignments only before pickup', async () => {
    dispatchService.reassignCourier.mockResolvedValue({})
    dispatchService.cancelAssignment.mockResolvedValue({})
    render(<DispatcherDashboardPage />)

    fireEvent.click(await screen.findByRole('tab', { name: /Active assignments/ }))
    expect(screen.getByRole('button', { name: 'Reassign SD-0003' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Cancel assignment SD-0003' })).toBeDisabled()

    fireEvent.click(screen.getByRole('button', { name: 'Reassign SD-0002' }))
    const dialog = screen.getByRole('dialog', { name: 'Reassign courier' })
    // The current courier cannot be chosen again.
    expect(within(dialog).getByRole('option', { name: 'Amal · 2 active · Available' })).toBeDisabled()
    fireEvent.change(within(dialog).getByLabelText('Courier'), { target: { value: '11' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Reassign' }))
    await waitFor(() => expect(dispatchService.reassignCourier).toHaveBeenCalledWith('p-2', '11'))

    fireEvent.click(screen.getByRole('button', { name: 'Cancel assignment SD-0002' }))
    await waitFor(() => expect(dispatchService.cancelAssignment).toHaveBeenCalledWith('p-2'))
  })

  it('shows parcel details with its status events and assignments', async () => {
    dispatchService.getParcel.mockResolvedValue({
      parcel: { ...WAITING, pickupAddress: 'Colombo 03', deliveryAddress: 'Kandy Town' },
      history: [{ previousStatus: null, newStatus: 'CREATED', changedAt: '2026-11-14T10:00:00Z' }],
      assignments: [assignment({ parcelId: 'p-1', state: 'CANCELLED' })],
    })
    render(<DispatcherDashboardPage />)

    fireEvent.click(await screen.findByRole('button', { name: 'SD-0001' }))

    const dialog = await screen.findByRole('dialog', { name: 'SD-0001' })
    expect(within(dialog).getByText('Colombo 03 → Kandy Town')).toBeInTheDocument()
    expect(within(dialog).getByText('cancelled', { exact: false })).toBeInTheDocument()
  })
})
