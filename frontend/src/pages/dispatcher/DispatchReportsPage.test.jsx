import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import DispatchReportsPage from './DispatchReportsPage'
import { dispatchService } from '../../services/dispatchService'

vi.mock('../../services/dispatchService', () => ({ dispatchService: { getSummary: vi.fn() } }))

const SUMMARY = {
  deliveriesPerDay: [
    { date: '2026-11-01', delivered: 1, failed: 1, total: 2 },
    { date: '2026-11-02', delivered: 2, failed: 0, total: 2 },
    { date: '2026-11-03', delivered: 0, failed: 0, total: 0 },
  ],
  successRate: 75, failureRate: 25,
  countsByStatus: [{ status: 'DELIVERED', count: 3 }, { status: 'FAILED', count: 1 }],
  activeDeliveriesPerCourier: [
    { courierId: 7, courierUserId: '70', courierName: 'Amal', count: 2 },
    { courierId: null, courierUserId: '90', courierName: null, count: 1 },
  ],
}

describe('DispatchReportsPage', () => {
  afterEach(() => vi.resetAllMocks())

  it('shows totals, rates, one bar per day and active deliveries by courier name', async () => {
    dispatchService.getSummary.mockResolvedValue(SUMMARY)
    render(<DispatchReportsPage />)

    expect(await screen.findByText('75.00%')).toBeInTheDocument()
    expect(screen.getByText('25.00%')).toBeInTheDocument()
    expect(screen.getAllByTestId('daily-bar')).toHaveLength(3)
    expect(screen.getByRole('img', { name: /2026-11-01 1 delivered, 1 failed/ })).toBeInTheDocument()
    expect(screen.getByText('Amal')).toBeInTheDocument()
    expect(screen.getByText('Courier user 90')).toBeInTheDocument()
  })

  it('requests the chosen date range and rejects a reversed one', async () => {
    dispatchService.getSummary.mockResolvedValue(SUMMARY)
    render(<DispatchReportsPage />)
    await screen.findByText('75.00%')

    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-11-01' } })
    fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-11-03' } })
    fireEvent.click(screen.getByRole('button', { name: 'Apply dates' }))
    await waitFor(() => expect(dispatchService.getSummary).toHaveBeenLastCalledWith(
      { from: '2026-11-01', to: '2026-11-03' }))

    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-11-05' } })
    fireEvent.click(screen.getByRole('button', { name: 'Apply dates' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Start date must not be after end date.')
  })
})
