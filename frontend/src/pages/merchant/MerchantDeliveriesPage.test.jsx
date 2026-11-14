import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import MerchantDeliveriesPage from './MerchantDeliveriesPage'
import MerchantNav from '../../components/MerchantNav'
import { webhookDeliveries } from '../../services/webhookDeliveries'

vi.mock('../../services/webhookDeliveries', () => ({ webhookDeliveries: {
  list: vi.fn(), detail: vi.fn(), retry: vi.fn(),
} }))
const row = { id: 'one', eventId: 'event-1', state: 'FAILED', parcelStatus: 'DELIVERED', trackingNumber: 'SD-1', attemptCount: 5, createdAt: '2026-11-14T10:00:00Z', merchantOrderReference: 'STORE-1' }
const show = () => render(<MemoryRouter><MerchantDeliveriesPage /></MemoryRouter>)
beforeEach(() => {
  vi.resetAllMocks()
  webhookDeliveries.list.mockResolvedValue({ items: [], total: 0 })
  Object.assign(HTMLDialogElement.prototype, {
    showModal() { this.setAttribute('open', '') }, close() { this.removeAttribute('open') },
  })
})

it('shows empty history and directs merchants to settings to send a test', async () => {
  show()
  expect(await screen.findByText('No webhook deliveries found.')).toBeInTheDocument()
  expect(screen.getByText(/Send a test event from Endpoint settings/)).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled()
  expect(screen.getByText('Showing 0–0 of 0')).toBeInTheDocument()
})

it('filters failures and preserves the table when a retry request fails', async () => {
  webhookDeliveries.list.mockResolvedValue({ items: [row], total: 1 })
  webhookDeliveries.retry.mockRejectedValue(new Error('Session expired'))
  show()
  await screen.findByText('SD-1')
  fireEvent.click(screen.getByLabelText('Failed only'))
  await waitFor(() => expect(webhookDeliveries.list).toHaveBeenLastCalledWith(0, true, expect.any(AbortSignal)))
  fireEvent.click(await screen.findByRole('button', { name: 'Retry' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Session expired')
  expect(screen.getByText('SD-1')).toBeInTheDocument()
})

it('shows delivered, queued and scheduled results, and only offers manual retry for failures', async () => {
  webhookDeliveries.list.mockResolvedValue({ items: [row, { ...row, id: 'two', state: 'DELIVERED' }, { ...row, id: 'three', state: 'RETRY_SCHEDULED', nextRetryAt: '2026-11-14T11:00:00Z' }, { ...row, id: 'four', state: 'PENDING' }], total: 4 })
  show()
  await screen.findByText('Retry Scheduled')
  expect(screen.getByText('Delivered')).toBeInTheDocument()
  expect(screen.getByText('Queued')).toBeInTheDocument()
  expect(screen.getAllByRole('button', { name: 'Retry' })).toHaveLength(1)
})

it('opens every retained attempt and order reference in the details dialog', async () => {
  webhookDeliveries.list.mockResolvedValue({ items: [row], total: 1 })
  webhookDeliveries.detail.mockResolvedValue({ delivery: row, attempts: [
    { attemptNumber: 1, outcome: 'RETRY_SCHEDULED', httpStatus: 503, failureCode: 'HTTP_ERROR', attemptedAt: '2026-11-14T10:00:00Z', nextRetryAt: '2026-11-14T10:01:00Z' },
    { attemptNumber: 2, outcome: 'DELIVERED', httpStatus: 204, attemptedAt: '2026-11-14T10:01:00Z' },
  ] })
  show()
  fireEvent.click(await screen.findByRole('button', { name: 'View attempts for SD-1' }))
  const dialog = await screen.findByRole('dialog')
  expect(webhookDeliveries.detail).toHaveBeenCalledWith('one', expect.any(AbortSignal))
  expect(dialog).toHaveTextContent('event-1')
  expect(dialog).toHaveTextContent('STORE-1')
  expect(dialog).toHaveTextContent('HTTP 503')
  expect(dialog).toHaveTextContent('HTTP 204')
  expect(within(dialog).getAllByRole('listitem')).toHaveLength(2)
  fireEvent.click(within(dialog).getByRole('button', { name: 'Close dialog' }))
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
})

it('queues a retry once, then refreshes history without pretending it succeeded', async () => {
  webhookDeliveries.list.mockResolvedValueOnce({ items: [row], total: 1 }).mockResolvedValue({ items: [{ ...row, state: 'PENDING' }], total: 1 })
  let finish
  webhookDeliveries.retry.mockImplementation(() => new Promise((resolve) => { finish = resolve }))
  show()
  const button = await screen.findByRole('button', { name: 'Retry' })
  fireEvent.click(button); fireEvent.click(button)
  expect(webhookDeliveries.retry).toHaveBeenCalledTimes(1)
  finish({ state: 'PENDING' })
  expect(await screen.findByText('Retry queued. Previous attempts are retained.')).toBeInTheDocument()
  expect(await screen.findByText('Queued')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument()
})

it('paginates and resets to page one when the failed filter changes', async () => {
  webhookDeliveries.list.mockResolvedValue({ items: [row], total: 21 })
  show(); await screen.findByText('SD-1')
  fireEvent.click(screen.getByRole('button', { name: 'Next page' }))
  await waitFor(() => expect(webhookDeliveries.list).toHaveBeenLastCalledWith(1, false, expect.any(AbortSignal)))
  await screen.findByText('Page 2 of 2')
  expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled()
  fireEvent.click(screen.getByLabelText('Failed only'))
  await waitFor(() => expect(webhookDeliveries.list).toHaveBeenLastCalledWith(0, true, expect.any(AbortSignal)))
})

it('recovers from history errors using Try again', async () => {
  webhookDeliveries.list.mockRejectedValueOnce(new Error('Service unavailable')).mockResolvedValue({ items: [row], total: 1 })
  show()
  expect(await screen.findByRole('alert')).toHaveTextContent('Service unavailable')
  fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
  expect(await screen.findByText('SD-1')).toBeInTheDocument()
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
})

it('ignores a stale response after the filter changes', async () => {
  let finishOld
  webhookDeliveries.list.mockImplementationOnce(() => new Promise((resolve) => { finishOld = resolve }))
    .mockResolvedValue({ items: [row], total: 1 })
  show()
  fireEvent.click(screen.getByLabelText('Failed only'))
  await screen.findByText('SD-1')
  finishOld({ items: [{ ...row, trackingNumber: 'STALE' }], total: 1 })
  await waitFor(() => expect(screen.queryByText('STALE')).not.toBeInTheDocument())
  expect(screen.getByText('SD-1')).toBeInTheDocument()
})

it('keeps the single Webhooks sidebar entry active on Delivery History', () => {
  render(<MemoryRouter initialEntries={['/merchant/webhook-deliveries']}><MerchantNav /></MemoryRouter>)
  const link = screen.getByRole('link', { name: 'Webhooks' })
  expect(link).toHaveClass('active')
  expect(link).toHaveAttribute('aria-current', 'page')
})
