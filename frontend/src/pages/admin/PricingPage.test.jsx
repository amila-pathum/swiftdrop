import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../services/apiClient'
import { pricingService } from '../../services/pricingService'
import PricingPage from './PricingPage'

vi.mock('../../services/pricingService', async (importOriginal) => ({
  ...await importOriginal(),
  pricingService: {
    getRateCard: vi.fn(), getZones: vi.fn(), publishRates: vi.fn(),
    getRateVersions: vi.fn(), getDeliveryQuote: vi.fn(),
  },
}))

const WP = { id: 'w', name: 'Western Metro', code: 'WP' }
const CP = { id: 'c', name: 'Central Hills', code: 'CP' }
const route = (origin, destination, rates) => ({
  origin, destination,
  baseWeightKg: null, baseFee: null, additionalKgFee: null, expressMultiplier: null,
  ...rates,
})
const CARD = {
  version: 3,
  publishedAt: '2025-01-01T00:00:00Z',
  publishedBy: 'admin-1',
  sizeSurcharges: { small: 0, medium: 150, large: 300 },
  routes: [
    route(CP, CP),
    route(CP, WP, { baseWeightKg: 1, baseFee: 350, additionalKgFee: 100, expressMultiplier: 1.5 }),
    route(WP, WP),
  ],
}
const ZONES = [
  { ...CP, localities: ['Kandy'], active: true },
  { ...WP, localities: ['Colombo'], active: true },
]
const cell = (label, from, to) => screen.getByLabelText(`${label} for ${from} to ${to}`)

describe('PricingPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    pricingService.getRateCard.mockResolvedValue(CARD)
    pricingService.getZones.mockResolvedValue(ZONES)
  })
  afterEach(() => vi.useRealTimers())

  it('shows the published version and every zone pair, with publishing off until something changes', async () => {
    render(<PricingPage />)
    expect(await screen.findByText('v3')).toBeInTheDocument()
    expect(screen.getByText(/effective 1 Jan 2025/)).toBeInTheDocument()
    expect(cell('Base fee (LKR)', 'Central Hills', 'Western Metro')).toHaveValue(350)
    expect(cell('Base fee (LKR)', 'Western Metro', 'Western Metro')).toHaveValue(null)
    expect(screen.queryByLabelText(/^Base weight.* for /)).not.toBeInTheDocument()
    expect(screen.getByLabelText('Base weight (kg)')).toHaveValue(1)
    expect(screen.getByLabelText('Medium')).toHaveValue(150)
    expect(screen.getByRole('button', { name: 'Save and publish' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Discard draft' })).toBeDisabled()
  })

  it('publishes only filled routes, based on the loaded version', async () => {
    pricingService.publishRates.mockResolvedValue({ ...CARD, version: 4 })
    render(<PricingPage />)
    await screen.findByText('v3')
    fireEvent.change(cell('Base fee (LKR)', 'Western Metro', 'Western Metro'), { target: { value: '300' } })
    fireEvent.change(cell('Each additional kg (LKR)', 'Western Metro', 'Western Metro'), { target: { value: '80' } })
    fireEvent.change(cell('Express ×', 'Western Metro', 'Western Metro'), { target: { value: '1.25' } })
    fireEvent.change(screen.getByLabelText('Large'), { target: { value: '275' } })
    fireEvent.change(screen.getByLabelText('Base weight (kg)'), { target: { value: '0.5' } })
    expect(screen.getByText('You have unpublished changes.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Save and publish' }))

    await waitFor(() => expect(pricingService.publishRates).toHaveBeenCalledWith({
      basedOnVersion: 3,
      sizeSurcharges: { small: 0, medium: 150, large: 275 },
      routes: [
        // The one base weight applies to every route.
        { originZoneId: 'c', destinationZoneId: 'w', baseWeightKg: 0.5, baseFee: 350, additionalKgFee: 100, expressMultiplier: 1.5 },
        { originZoneId: 'w', destinationZoneId: 'w', baseWeightKg: 0.5, baseFee: 300, additionalKgFee: 80, expressMultiplier: 1.25 },
      ],
    }))
    expect(await screen.findByText(/Published rate version v4/)).toBeInTheDocument()
  })

  it('refuses half-filled rows and invalid values before calling the API', async () => {
    render(<PricingPage />)
    await screen.findByText('v3')
    fireEvent.change(cell('Base fee (LKR)', 'Central Hills', 'Central Hills'), { target: { value: '300' } })
    fireEvent.change(cell('Express ×', 'Central Hills', 'Western Metro'), { target: { value: '0.5' } })
    fireEvent.change(screen.getByLabelText('Base weight (kg)'), { target: { value: '-1' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save and publish' }))

    expect(screen.getByText('Fix the 4 highlighted values before publishing.')).toBeInTheDocument()
    expect(screen.getByText('Base weight cannot be negative')).toBeInTheDocument()
    expect(cell('Each additional kg (LKR)', 'Central Hills', 'Central Hills')).toHaveAttribute('aria-invalid', 'true')
    expect(cell('Express ×', 'Central Hills', 'Western Metro'))
      .toHaveAttribute('title', 'Express × must be at least 1')
    expect(pricingService.publishRates).not.toHaveBeenCalled()
  })

  it('discards the draft, and offers a reload when someone else published first', async () => {
    pricingService.publishRates.mockRejectedValue(new ApiError('Version v4 was published while you were editing.', { status: 409 }))
    render(<PricingPage />)
    await screen.findByText('v3')
    const fee = cell('Base fee (LKR)', 'Central Hills', 'Western Metro')
    fireEvent.change(fee, { target: { value: '400' } })
    fireEvent.change(screen.getByLabelText('Base weight (kg)'), { target: { value: '2' } })
    fireEvent.click(screen.getByRole('button', { name: 'Discard draft' }))
    expect(fee).toHaveValue(350)
    expect(screen.getByLabelText('Base weight (kg)')).toHaveValue(1)

    fireEvent.change(fee, { target: { value: '400' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save and publish' }))
    const alert = await screen.findByText(/was published while you were editing/)
    fireEvent.click(within(alert.closest('[role="alert"], .alert') ?? document.body)
      .getByRole('button', { name: 'Reload latest rates' }))
    await waitFor(() => expect(pricingService.getRateCard).toHaveBeenCalledTimes(2))
  })

  it('previews a quote with the published rates', async () => {
    pricingService.getDeliveryQuote.mockResolvedValue({
      amount: 450, currency: 'LKR', breakdown: { baseFee: 350, weightFee: 100, sizeFee: 0, expressFee: 0 },
    })
    render(<PricingPage />)
    await screen.findByText('v3')
    vi.useFakeTimers()
    fireEvent.change(screen.getByLabelText('From'), { target: { value: 'Colombo' } })
    fireEvent.change(screen.getByLabelText('To'), { target: { value: 'Kandy' } })
    fireEvent.change(screen.getByLabelText('Weight'), { target: { value: '2' } })
    await act(async () => { await vi.advanceTimersByTimeAsync(300) })
    vi.useRealTimers()

    const preview = screen.getByRole('complementary', { name: 'Quote preview' })
    expect(await within(preview).findByText('Total')).toBeInTheDocument()
    expect(within(preview).getByText('Total').nextSibling).toHaveTextContent('450')
    expect(within(preview).queryByText('Express', { selector: 'dt' })).not.toBeInTheDocument()
    expect(pricingService.getDeliveryQuote).toHaveBeenCalledTimes(1)
    expect(pricingService.getDeliveryQuote).toHaveBeenCalledWith({
      pickupCity: 'Colombo', deliveryCity: 'Kandy', weight: '2', serviceType: 'STANDARD', size: 'SMALL',
    }, { signal: expect.any(AbortSignal) })
  })

  it('starts at 1 kg before anything is published, and shows the published base weight after', async () => {
    pricingService.getRateCard.mockResolvedValueOnce({
      ...CARD, version: null, publishedAt: null, routes: [route(CP, WP), route(WP, WP)],
    })
    const view = render(<PricingPage />)
    expect(await screen.findByLabelText('Base weight (kg)')).toHaveValue(1)
    view.unmount()

    pricingService.getRateCard.mockResolvedValueOnce({
      ...CARD, routes: CARD.routes.map((r) => (r.baseFee ? { ...r, baseWeightKg: 2.5 } : r)),
    })
    render(<PricingPage />)
    expect(await screen.findByLabelText('Base weight (kg)')).toHaveValue(2.5)
    expect(screen.getByRole('button', { name: 'Save and publish' })).toBeDisabled()
  })
})
