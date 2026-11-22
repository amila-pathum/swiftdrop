import { fireEvent, render, screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { useCourierDelivery } from '../../../hooks/courier/useCourierDeliveries'
import CourierDeliveryDetailPage from './CourierDeliveryDetailPage'

vi.mock('../../../hooks/courier/useCourierDeliveries', () => ({ useCourierDelivery: vi.fn() }))
vi.mock('./components/DeliveryUpdateForm', () => ({ default: () => <p>Delivery confirmation form</p> }))

function fixture() {
  return {
    assignment: { id: 'a', active: true, deliveryDate: '2026-11-14' },
    parcel: { id: 'p', trackingNumber: 'SD10480015', status: 'PICKED_UP', receiverName: 'Asanka Rajapaksha',
      receiverPhone: '+94 77 202 8890', deliveryAddress: '7 Hospital Road, Dehiwala 10350',
      pickupAddress: '8 Lake Drive, Kandy', pickupContactName: 'Priya Jayasinghe', pickupContactPhone: '+94 71 555 0192',
      contentsDescription: 'Clothing', weight: 2.5, size: 'SMALL', lengthCm: 25, widthCm: 20, heightCm: 10,
      history: [
        { id: 'h1', status: 'CREATED', recordedAt: '2026-11-14T07:21:00Z', locationDescription: 'Kandy', attemptNo: 1, actorRole: 'CUSTOMER' },
        { id: 'h2', status: 'PICKED_UP', recordedAt: '2026-11-14T08:17:00Z', locationDescription: 'Kandy', attemptNo: 1, actorRole: 'COURIER' },
      ] }, tracking: [], proofs: [],
  }
}
function show() {
  return render(<MemoryRouter initialEntries={['/courier/deliveries/p']}><Routes>
    <Route path="/courier/deliveries/:parcelId" element={<CourierDeliveryDetailPage />} />
  </Routes></MemoryRouter>)
}

describe('courier delivery detail', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useCourierDelivery.mockReturnValue({ data: fixture(), loading: false, error: null, reload: vi.fn() })
  })
  it('shows the recipient, call link, separate contacts and parcel facts from the screenshot', () => {
    show()
    expect(screen.getByRole('heading', { level: 1, name: 'SD10480015' })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Breadcrumb' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Call +94 77 202 8890' })).toHaveAttribute('href', 'tel:+94772028890')
    expect(within(screen.getByRole('region', { name: 'Pickup' })).getByText('Priya Jayasinghe')).toBeInTheDocument()
    expect(within(screen.getByRole('region', { name: 'Drop-off' })).getByText('Asanka Rajapaksha')).toBeInTheDocument()
    const parcel = within(screen.getByRole('region', { name: 'Parcel' }))
    for (const value of ['Clothing', '2.5 kg', '25 × 20 × 10 cm (Small)', '14 Nov 2026']) expect(parcel.getByText(value)).toBeInTheDocument()
  })
  it('renders confirmed history newest first and keeps future timeline stages pending', () => {
    show()
    const history = within(screen.getByRole('list', { name: 'Delivery history' })).getAllByRole('listitem')
    expect(within(history[0]).getByText('Picked up')).toBeInTheDocument()
    expect(within(history[0]).getByText('Attempt 1 · by Courier')).toBeInTheDocument()
    expect(within(history[1]).getByText('Attempt 1 · by Customer')).toBeInTheDocument()
    expect(within(history[0]).getByText('14 Nov 2026, 13:47')).toBeInTheDocument()
    const stages = within(screen.getByRole('list', { name: 'Delivery progress' })).getAllByRole('listitem')
    expect(stages[1]).toHaveAttribute('aria-current', 'step')
    expect(stages[2]).not.toHaveClass('is-reached')
  })
  it('shortens Out for delivery to Out in the history list only', () => {
    const data = fixture()
    data.parcel.status = 'OUT_FOR_DELIVERY'
    data.parcel.history.push({ id: 'h3', status: 'OUT_FOR_DELIVERY', recordedAt: '2026-11-14T09:00:00Z', attemptNo: 1, actorRole: 'COURIER' })
    useCourierDelivery.mockReturnValue({ data, loading: false, error: null, reload: vi.fn() })
    show()
    const history = within(screen.getByRole('list', { name: 'Delivery history' })).getAllByRole('listitem')
    expect(within(history[0]).getByText('Out')).toBeInTheDocument()
    expect(within(history[0]).queryByText('Out for delivery')).not.toBeInTheDocument()
    expect(within(screen.getByRole('list', { name: 'Delivery progress' })).getByText('Out for delivery')).toBeInTheDocument()
  })
  it('opens the existing confirmation form from the full-width bottom action', () => {
    show()
    expect(screen.queryByText('Delivery confirmation form')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Mark In Transit' }))
    expect(screen.getByText('Delivery confirmation form')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Close update' }))
    expect(screen.getByRole('button', { name: 'Mark In Transit' })).toBeInTheDocument()
  })
  it('does not fabricate missing contacts, dimensions, contents or history', () => {
    const data = fixture()
    data.parcel = { id: 'p', trackingNumber: 'SD1', status: 'CREATED' }
    useCourierDelivery.mockReturnValue({ data, loading: false, error: null, reload: vi.fn() })
    show()
    expect(screen.queryByRole('link', { name: /Call/ })).not.toBeInTheDocument()
    expect(screen.getByText('Pickup contact not provided')).toBeInTheDocument()
    expect(screen.getByText('No confirmed history is available yet.')).toBeInTheDocument()
    expect(screen.queryByText('Clothing')).not.toBeInTheDocument()
    expect(screen.queryByText(/25 ×/)).not.toBeInTheDocument()
  })
  it('does not mark unreached delivery stages complete after cancellation', () => {
    const data = fixture()
    data.parcel.status = 'CANCELLED'
    data.parcel.history = data.parcel.history.slice(0, 1)
    useCourierDelivery.mockReturnValue({ data, loading: false, error: null, reload: vi.fn() })
    show()
    const stages = within(screen.getByRole('list', { name: 'Delivery progress' })).getAllByRole('listitem')
    expect(stages[0]).toHaveClass('is-reached')
    expect(stages[1]).not.toHaveClass('is-reached')
    expect(screen.queryByRole('button', { name: 'Mark In Transit' })).not.toBeInTheDocument()
  })
})
