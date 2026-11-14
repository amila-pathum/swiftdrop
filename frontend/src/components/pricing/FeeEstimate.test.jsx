import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import FeeEstimate from './FeeEstimate'
import { getDeliveryQuote } from '../../services/pricingService'

vi.mock('../../services/pricingService', () => ({ getDeliveryQuote: vi.fn() }))
const quote = { amount: 1050, currency: 'LKR', breakdown: { baseFee: 350, weightFee: 200, sizeFee: 150, expressFee: 350 } }
const props = { pickupCity: 'Colombo', deliveryCity: 'Kandy', weight: '2.3', serviceType: 'EXPRESS', size: 'MEDIUM' }
beforeEach(() => vi.resetAllMocks())

it('shows the amount and breakdown and reports success to the form', async () => {
  const onQuote = vi.fn()
  getDeliveryQuote.mockResolvedValue(quote)
  render(<FeeEstimate {...props} onQuote={onQuote} />)
  fireEvent.click(screen.getByRole('button', { name: 'Get Estimate' }))
  expect(screen.getByRole('button', { name: 'Calculating…' })).toBeDisabled()
  expect(await screen.findByText(/Estimated fee/)).toHaveTextContent('1,050.00')
  expect(screen.getByText(/Base fee:/)).toHaveTextContent('350.00')
  expect(screen.getByText(/Additional weight:/)).toHaveTextContent('200.00')
  expect(screen.getByText(/Size:/)).toHaveTextContent('150.00')
  expect(screen.getByText(/Express:/)).toHaveTextContent('350.00')
  expect(getDeliveryQuote).toHaveBeenCalledWith(props, { signal: expect.any(AbortSignal) })
  await waitFor(() => expect(onQuote).toHaveBeenLastCalledWith(quote))
})

it('discards stale responses when price inputs change, including changing back', async () => {
  let resolveOld
  getDeliveryQuote.mockReturnValueOnce(new Promise((resolve) => { resolveOld = resolve }))
  const onQuote = vi.fn()
  const view = render(<FeeEstimate {...props} onQuote={onQuote} />)
  fireEvent.click(screen.getByRole('button', { name: 'Get Estimate' }))
  view.rerender(<FeeEstimate {...props} weight="3" onQuote={onQuote} />)
  view.rerender(<FeeEstimate {...props} onQuote={onQuote} />)
  await act(async () => resolveOld(quote))
  expect(screen.queryByText(/Estimated fee/)).not.toBeInTheDocument()
  expect(onQuote).toHaveBeenLastCalledWith(null)
  expect(getDeliveryQuote.mock.calls[0][1].signal.aborted).toBe(true)
})

it('invalidates a successful quote and reports API errors without enabling confirmation', async () => {
  const onQuote = vi.fn()
  getDeliveryQuote.mockResolvedValueOnce(quote).mockRejectedValueOnce(new Error('No active zone found for city: Kandy'))
  const view = render(<FeeEstimate {...props} onQuote={onQuote} />)
  fireEvent.click(screen.getByRole('button', { name: 'Get Estimate' }))
  await screen.findByText(/Estimated fee/)
  view.rerender(<FeeEstimate {...props} serviceType="STANDARD" onQuote={onQuote} />)
  expect(onQuote).toHaveBeenLastCalledWith(null)
  fireEvent.click(screen.getByRole('button', { name: 'Get Estimate' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('No active zone')
  expect(onQuote).toHaveBeenLastCalledWith(null)
})

it('rejects invalid inputs without calling the quote API', () => {
  const view = render(<FeeEstimate {...props} weight="0" onQuote={vi.fn()} />)
  fireEvent.click(screen.getByRole('button', { name: 'Get Estimate' }))
  expect(screen.getByRole('alert')).toHaveTextContent('positive weight')
  view.rerender(<FeeEstimate {...props} size="" onQuote={vi.fn()} />)
  fireEvent.click(screen.getByRole('button', { name: 'Get Estimate' }))
  expect(screen.getByRole('alert')).toHaveTextContent('a size')
  expect(getDeliveryQuote).not.toHaveBeenCalled()
})

it('hides the express line for standard deliveries', async () => {
  getDeliveryQuote.mockResolvedValue({ ...quote, amount: 700, breakdown: { ...quote.breakdown, expressFee: 0 } })
  render(<FeeEstimate {...props} serviceType="STANDARD" onQuote={vi.fn()} />)
  fireEvent.click(screen.getByRole('button', { name: 'Get Estimate' }))
  await screen.findByText(/Estimated fee/)
  expect(screen.queryByText(/Express:/)).not.toBeInTheDocument()
})
