import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { courierService } from '../../../../services/courierService'
import DeliveryUpdateForm from './DeliveryUpdateForm'
vi.mock('../../../../services/courierService', () => ({ courierService: { submit: vi.fn(), getSubmission: vi.fn(), uploadPhoto: vi.fn() } }))
describe('delivery proof form', () => {
  beforeEach(() => vi.clearAllMocks())
  it('blocks incomplete proof and sends the recipient name', async () => {
    courierService.submit.mockResolvedValue({ syncStatus: 'CONFIRMED' })
    const confirmed = vi.fn()
    render(<DeliveryUpdateForm parcel={{ id: 'p', status: 'OUT_FOR_DELIVERY' }} onConfirmed={confirmed} />)
    fireEvent.click(screen.getByRole('button', { name: 'Confirm delivered' }))
    expect(courierService.submit).not.toHaveBeenCalled()
    expect(screen.getByText('Enter the recipient name')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText(/Recipient name/), { target: { value: 'Amal' } })
    fireEvent.click(screen.getByRole('button', { name: 'Confirm delivered' }))
    await waitFor(() => expect(confirmed).toHaveBeenCalledOnce())
    expect(screen.queryByLabelText('Location description')).not.toBeInTheDocument()
    expect(courierService.submit).toHaveBeenCalledWith('p', expect.objectContaining({ status: 'DELIVERED', recipientName: 'Amal', submissionId: expect.any(String) }))
  })
  it('offers an optional photo before delivery and sends its key with the update', async () => {
    courierService.submit.mockResolvedValue({ syncStatus: 'CONFIRMED' })
    courierService.uploadPhoto.mockResolvedValue('proofs/p/pickup.png')
    render(<DeliveryUpdateForm parcel={{ id: 'p', status: 'CREATED' }} onConfirmed={vi.fn()} />)
    expect(screen.queryByLabelText(/Recipient name/)).not.toBeInTheDocument()
    const photo = new File(['png'], 'pickup.png', { type: 'image/png' })
    fireEvent.change(screen.getByLabelText('Photo (optional)'), { target: { files: [photo] } })
    fireEvent.click(screen.getByRole('button', { name: 'Confirm picked up' }))
    await waitFor(() => expect(courierService.submit).toHaveBeenCalledWith('p', expect.objectContaining({ status: 'PICKED_UP', photoKey: 'proofs/p/pickup.png' })))
    expect(courierService.uploadPhoto).toHaveBeenCalledWith('p', photo)
  })
  it('keeps pending proof locked and does not announce successful delivery', async () => {
    courierService.submit.mockResolvedValue({ syncStatus: 'PENDING' })
    const confirmed = vi.fn()
    render(<DeliveryUpdateForm parcel={{ id: 'p', status: 'OUT_FOR_DELIVERY' }} onConfirmed={confirmed} />)
    fireEvent.change(screen.getByLabelText(/Recipient name/), { target: { value: 'Amal' } })
    fireEvent.click(screen.getByRole('button', { name: 'Confirm delivered' }))
    await screen.findByRole('button', { name: 'Check confirmation' })
    expect(screen.getByLabelText(/Recipient name/)).toBeDisabled()
    expect(confirmed).not.toHaveBeenCalled()
  })
})
