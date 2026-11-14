import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { parcelService } from '../../services/parcelService'
import { getDeliveryQuote } from '../../services/pricingService'
import CreateDeliveryPage from './CreateDeliveryPage'

vi.mock('../../services/parcelService', () => ({
  parcelService: { createParcel: vi.fn() },
}))
vi.mock('../../services/pricingService', () => ({ getDeliveryQuote: vi.fn() }))

describe('CreateDeliveryPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    parcelService.createParcel.mockResolvedValue({
      id: '9e2446f4-66e5-44bb-8639-e5625feb6876',
      trackingNumber: 'SD-TEST-001',
      status: 'CREATED',
      charge: 487.5,
      createdAt: '2026-11-14T10:00:00Z',
    })
    getDeliveryQuote.mockResolvedValue({
      amount: 487.5,
      currency: 'LKR',
      breakdown: { baseFee: 300, weightFee: 187.5, expressFee: 0 },
    })
  })

  it('submits split addresses and selected districts using the parcel contract', async () => {
    render(<MemoryRouter><CreateDeliveryPage /></MemoryRouter>)

    expect(screen.getByLabelText(/^Pickup district/).options).toHaveLength(26)
    fireEvent.change(screen.getByLabelText(/^Pickup address line 1/), {
      target: { value: '10 Main Street' },
    })
    fireEvent.change(screen.getByLabelText('Pickup address line 2 (optional)'), {
      target: { value: 'Floor 2' },
    })
    fireEvent.change(screen.getByLabelText(/^Pickup district/), { target: { value: 'Colombo' } })
    fireEvent.click(screen.getByRole('button', { name: 'Continue →' }))

    fireEvent.change(screen.getByLabelText(/^Receiver name/), { target: { value: 'Nimal' } })
    fireEvent.change(screen.getByLabelText(/^Receiver phone/), { target: { value: '0771234567' } })
    fireEvent.change(screen.getByLabelText(/^Delivery address line 1/), {
      target: { value: '20 Lake Road' },
    })
    fireEvent.change(screen.getByLabelText(/^Delivery district/), { target: { value: 'Kandy' } })
    fireEvent.click(screen.getByRole('button', { name: 'Continue →' }))

    fireEvent.change(screen.getByLabelText(/^Weight \(kg\)/), { target: { value: '2.5' } })
    fireEvent.change(screen.getByLabelText(/^Parcel size/), { target: { value: 'MEDIUM' } })
    fireEvent.click(screen.getByRole('button', { name: 'Get Estimate' }))
    expect(await screen.findByText(/Estimated fee/)).toBeInTheDocument()
    expect(getDeliveryQuote).toHaveBeenCalledWith({
      pickupCity: 'Colombo',
      deliveryCity: 'Kandy',
      weight: '2.5',
      serviceType: 'STANDARD',
    })
    fireEvent.click(screen.getByRole('button', { name: 'Continue →' }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirm Delivery' }))

    expect(parcelService.createParcel).toHaveBeenCalledWith({
      receiverName: 'Nimal',
      receiverPhone: '0771234567',
      pickupAddressLine1: '10 Main Street',
      pickupAddressLine2: 'Floor 2',
      pickupCity: 'Colombo',
      deliveryAddressLine1: '20 Lake Road',
      deliveryAddressLine2: null,
      deliveryCity: 'Kandy',
      weight: 2.5,
      size: 'MEDIUM',
      parcelType: 'STANDARD',
    })
    expect(await screen.findByText('SD-TEST-001')).toBeInTheDocument()
    expect(screen.getByText(/Status:/)).toHaveTextContent('CREATED')
    expect(screen.getByText(/Final delivery charge:/)).toHaveTextContent('LKR')
    expect(screen.getByText(/Parcel ID:/)).toHaveTextContent('9e2446f4-66e5-44bb-8639-e5625feb6876')
    expect(screen.getByRole('button', { name: 'Create Another Delivery' })).toBeInTheDocument()
  })
})
