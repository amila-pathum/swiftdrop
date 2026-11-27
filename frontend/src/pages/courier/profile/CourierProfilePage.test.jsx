import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useCourierProfile } from '../../../hooks/courier/useCourierProfile'
import { useCourierDeliveries } from '../../../hooks/courier/useCourierDeliveries'
import { courierService } from '../../../services/courierService'
import CourierProfilePage from './CourierProfilePage'

vi.mock('../../../hooks/useAuth', () => ({ useAuth: () => ({ user: { id: 42, email: 'kasun@example.com' } }) }))
vi.mock('../../../hooks/courier/useCourierProfile', () => ({ useCourierProfile: vi.fn() }))
vi.mock('../../../hooks/courier/useCourierDeliveries', () => ({ useCourierDeliveries: vi.fn() }))
vi.mock('../../../services/courierService', () => ({ courierService: { updateAvailability: vi.fn(), updateProfile: vi.fn() } }))

const profile = { id: 10, name: 'Kasun Fernando', phone: '077 123 4567', vehicleType: 'VAN', vehicleNumber: 'wp-cab-1234', area: 'Colombo',
  availability: 'AVAILABLE', active: true, createdAt: '2026-03-01T00:00:00Z', updatedAt: '2026-11-20T10:00:00Z' }
const delivery = (id, status) => ({ assignment: { id, active: true, deliveryDate: null }, parcel: { id, status } })

describe('courier profile', () => {
  const reload = vi.fn()
  beforeEach(() => {
    vi.clearAllMocks()
    useCourierProfile.mockReturnValue({ data: profile, reload })
    useCourierDeliveries.mockReturnValue({ data: [delivery('a', 'DELIVERED'), delivery('b', 'DELIVERED'), delivery('c', 'FAILED'), delivery('d', 'IN_TRANSIT')] })
  })

  it('shows identity, record, contact and vehicle details', () => {
    render(<CourierProfilePage />)
    const hero = screen.getByRole('region', { name: 'Courier summary' })
    expect(within(hero).getByRole('heading', { name: 'Kasun Fernando' })).toBeInTheDocument()
    expect(within(hero).getByText('KF')).toBeInTheDocument()
    expect(within(hero).getByText('Van courier · Colombo')).toBeInTheDocument()
    expect(within(hero).getByText('Courier since March 2026')).toBeInTheDocument()
    const record = screen.getByLabelText('Delivery record')
    expect(record).toHaveTextContent('2Completed deliveries')
    expect(record).toHaveTextContent('67%Success rate')
    expect(record).toHaveTextContent('1Active now')
    expect(within(screen.getByRole('region', { name: 'Contact details' })).getByRole('link', { name: '077 123 4567' })).toHaveAttribute('href', 'tel:0771234567')
    expect(within(screen.getByRole('region', { name: 'Vehicle' })).getByText('wp-cab-1234')).toBeInTheDocument()
  })

  it('changes availability with one click and ignores the current choice', async () => {
    courierService.updateAvailability.mockResolvedValue({})
    render(<CourierProfilePage />)
    expect(screen.getByRole('radio', { name: /Available/ })).toHaveAttribute('aria-checked', 'true')
    fireEvent.click(screen.getByRole('radio', { name: /Available/ }))
    expect(courierService.updateAvailability).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('radio', { name: /Offline/ }))
    await waitFor(() => expect(reload).toHaveBeenCalled())
    expect(courierService.updateAvailability).toHaveBeenCalledWith('OFFLINE')
  })

  it('opens the edit form and returns to the details on cancel', () => {
    render(<CourierProfilePage />)
    fireEvent.click(screen.getByRole('button', { name: 'Edit profile' }))
    expect(screen.getByRole('group', { name: 'Vehicle' })).toBeInTheDocument()
    expect(screen.getByLabelText(/Vehicle number/)).toHaveValue('wp-cab-1234')
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(screen.getByRole('region', { name: 'Contact details' })).toBeInTheDocument()
  })
})
