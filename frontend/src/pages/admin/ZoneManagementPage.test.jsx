import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../services/apiClient'
import { pricingService } from '../../services/pricingService'
import ZoneManagementPage from './ZoneManagementPage'

vi.mock('../../services/pricingService', () => ({
  pricingService: { getZones: vi.fn(), createZone: vi.fn(), updateZone: vi.fn() },
}))

const ZONES = [
  { id: 'z1', name: 'Central Hills', code: 'CP', localities: ['Kandy', 'Peradeniya'], active: true },
  { id: 'z2', name: 'Northern', code: 'NP', localities: ['Jaffna'], active: false },
]

describe('ZoneManagementPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    pricingService.getZones.mockResolvedValue(ZONES)
  })

  it('lists every zone with its code, localities and state', async () => {
    render(<ZoneManagementPage />)
    const row = (await screen.findByText('Central Hills')).closest('tr')
    expect(within(row).getByText('CP')).toBeInTheDocument()
    expect(within(row).getByText('Kandy, Peradeniya')).toBeInTheDocument()
    expect(within(row).getByText('Active')).toBeInTheDocument()
    expect(within(screen.getByText('Northern').closest('tr')).getByText('Inactive')).toBeInTheDocument()
    expect(pricingService.getZones).toHaveBeenCalledWith(expect.objectContaining({ includeInactive: true }))
  })

  it('adds a zone with cleaned-up localities', async () => {
    pricingService.createZone.mockResolvedValue({})
    render(<ZoneManagementPage />)
    fireEvent.click(await screen.findByRole('button', { name: '+ Add zone' }))
    const dialog = screen.getByRole('dialog', { name: 'Add zone' })
    fireEvent.change(within(dialog).getByLabelText(/^Zone name/), { target: { value: ' Southern Coast ' } })
    fireEvent.change(within(dialog).getByLabelText(/^Zone code/), { target: { value: 'sp' } })
    fireEvent.change(within(dialog).getByLabelText(/^Covered localities/), {
      target: { value: 'Galle,  Matara ,, galle, Hambantota' },
    })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Save zone' }))

    await waitFor(() => expect(pricingService.createZone).toHaveBeenCalledWith({
      name: 'Southern Coast', code: 'SP', localities: ['Galle', 'Matara', 'Hambantota'], active: true,
    }))
    expect(await screen.findByText('Southern Coast was added.')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(pricingService.getZones).toHaveBeenCalledTimes(2)
  })

  it('shows validation errors next to the fields without calling the API', async () => {
    render(<ZoneManagementPage />)
    fireEvent.click(await screen.findByRole('button', { name: '+ Add zone' }))
    fireEvent.change(screen.getByLabelText(/^Zone code/), { target: { value: 'W-P' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save zone' }))
    expect(screen.getByText('Zone name is required.')).toBeInTheDocument()
    expect(screen.getByText('Use 1 to 10 letters or digits.')).toBeInTheDocument()
    expect(screen.getByText('Add at least one city this zone covers.')).toBeInTheDocument()
    expect(pricingService.createZone).not.toHaveBeenCalled()
  })

  it('edits a zone, including turning it off, and shows server conflicts', async () => {
    pricingService.updateZone.mockRejectedValueOnce(
      new ApiError('Kandy is already covered by Central Hills', { status: 409 }))
    render(<ZoneManagementPage />)
    fireEvent.click(await screen.findByRole('button', { name: 'Edit Central Hills' }))
    const dialog = screen.getByRole('dialog', { name: 'Edit zone' })
    expect(within(dialog).getByLabelText(/^Covered localities/)).toHaveValue('Kandy, Peradeniya')
    fireEvent.click(within(dialog).getByLabelText('Zone is active for bookings'))
    fireEvent.click(within(dialog).getByRole('button', { name: 'Save zone' }))

    expect(await within(dialog).findByText('Kandy is already covered by Central Hills')).toBeInTheDocument()
    expect(pricingService.updateZone).toHaveBeenCalledWith('z1', {
      name: 'Central Hills', code: 'CP', localities: ['Kandy', 'Peradeniya'], active: false,
    })
  })
})
